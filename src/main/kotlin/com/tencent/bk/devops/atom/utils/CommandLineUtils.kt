/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bk.devops.atom.utils

import com.google.common.collect.EvictingQueue
import com.tencent.bk.devops.atom.common.ErrorCode
import com.tencent.bk.devops.atom.enums.CharsetType
import com.tencent.bk.devops.atom.exception.AtomException
import com.tencent.bk.devops.plugin.pojo.ErrorType
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.PumpStreamHandler
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.concurrent.ConcurrentLinkedQueue

object CommandLineUtils {
    /*一级队列保证日志输出，不阻塞子进程*/
    val outQueueFLevel: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    /*二级队列对输出进行处理，不阻塞日志输出*/
    private val outQueueSLevel: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
    private val errQueueFLevel: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
    private val errQueueSLevel: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
    private const val MAXIMUM_POOL_SIZE = 100
    private val logger = LoggerFactory.getLogger(CommandLineUtils::class.java)

    /*OUTPUT_NAME 正则匹配规则*/
    private val OUTPUT_NAME = Pattern.compile("name=([^,:=\\s]*)")

    /*OUTPUT_TYPE 正则匹配规则*/
    private val OUTPUT_TYPE = Pattern.compile("type=([^,:=\\s]*)")

    /*OUTPUT_LABEL 正则匹配规则*/
    private val OUTPUT_LABEL = Pattern.compile("label=([^,:=\\s]*)")

    /*OUTPUT_PATH 正则匹配规则*/
    private val OUTPUT_PATH = Pattern.compile("path=([^,:=\\s]*)")

    /*OUTPUT_REPORT_TYPE 正则匹配规则*/
    private val OUTPUT_REPORT_TYPE = Pattern.compile("reportType=([^,:=\\s]*)")

    /*OUTPUT_GATE_TITLE 正则匹配规则*/
    private val OUTPUT_GATE_TITLE = Pattern.compile("title=([^,:=\\s]*)")

    private val lineParser = listOf(OauthCredentialLineParser())
    private const val PIPELINE_TASK_MESSAGE_STRING_LENGTH_MAX = 3700
    private const val success = "0"

    class TaskRunContext {
        lateinit var tasKName: String
        lateinit var outTaskFLevel: Runnable
        lateinit var outTaskSLevel: Runnable
        lateinit var errTaskFLevel: Runnable
        lateinit var errTaskSLevel: Runnable
        var close: Boolean = false
        fun outTaskSLevelInit() = this::outTaskSLevel.isInitialized
        fun errTaskSLevelInit() = this::errTaskSLevel.isInitialized
    }


    @Suppress("LongParameterList")
    fun execute(
            cmdLine: CommandLine,
            workspace: File?,
            print2Logger: Boolean,
            prefix: String = "",
            executeErrorMessage: String? = null,
            buildId: String,
            charSetType: CharsetType? = null,
            context: TaskRunContext = TaskRunContext()
    ): String {
        logger.debug("will execute command >>> ${cmdLine.toStrings().joinToString()}")
        context.tasKName = cmdLine.toStrings().joinToString()
        val errString: EvictingQueue<Char> = EvictingQueue.create(PIPELINE_TASK_MESSAGE_STRING_LENGTH_MAX)
        /*解析命令*/
        /*生成executor*/
        val executor = CommandLineExecutor()
        if (workspace != null) {
            /*工作空间已知则装载进去*/
            executor.workingDirectory = workspace
        }
        /*获取上下文文件*/
        val contextLogFile = if (buildId.isNotBlank()) {
            ScriptEnvUtils.getContextFile(buildId)
        } else {
            null
        }

        /*获取字符集编码类型*/
        val charset = when (charSetType) {
            CharsetType.UTF_8 -> "UTF-8"
            CharsetType.GBK -> "GBK"
            else -> Charset.defaultCharset().name()
        }
        /*定义output标准输出流*/
        val outputStream = object : RunOutputStream(charset) {
            override fun processLine(line: String, lineCount: Int) {
                outQueueFLevel.add(line)
            }
        }
        /*定义error输出流*/
        val errStream = object : RunOutputStream(charset) {
            override fun processLine(line: String, lineCount: Int) {
                errQueueFLevel.add(line)
            }
        }
        /*定义好输出流*/
        executor.streamHandler = PumpStreamHandler(outputStream, errStream).apply { setStopTimeout(10_000) }
        var exitCode: Int = -1
        try {
            dealWithStdout(prefix = prefix,
                    print2Logger = print2Logger,
                    executor = executor,
                    contextLogFile = contextLogFile,
                    context = context)
            dealWithStderr(prefix = prefix,
                    print2Logger = print2Logger,
                    executor = executor,
                    contextLogFile = contextLogFile,
                    context = context,
                    errString = errString)
            adjusterTask(context)
            /*执行脚本*/
            exitCode = executor.execute(cmdLine)
        } catch (ignored: Throwable) {
            /*对其余异常兜底处理，可能是执行脚本时抛错的错。*/
            val errorMessage = executeErrorMessage ?: "Fail to execute the command"
            logger.warn(errorMessage, ignored)
            throw AtomException(
                    ignored.message ?: ""
            )
        } finally {
            logger.debug("sub process return $exitCode")
            closeExecutor(context)
        }
        if (exitCode != 0) {
            /*执行返回码，非零表示执行出错，这时直接抛错。为用户自己的脚本问题*/
            throw AtomException(
                    "$prefix Script command execution failed with exit code($exitCode) \n" +
                            "Error message tracking:\n" +
                            errString.joinToString("")
            )
        }
        return success
    }

    private val threadPoolExecutor = ThreadPoolExecutor(
            5,
            MAXIMUM_POOL_SIZE,
            1L,
            TimeUnit.SECONDS,
            SynchronousQueue()
    )

    fun shutdownThreadPool() {
        threadPoolExecutor.shutdownNow()
        try {
            // 等待线程池终止
            if (!threadPoolExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                logger.debug("线程池未能在60秒内终止")
            }
        } catch (interruptedException: InterruptedException) {
            logger.debug("等待线程池终止时被中断")
            Thread.currentThread().interrupt()
        }
    }

    private fun closeExecutor(context: TaskRunContext) {
        val thread = Thread {
            while (true) {
                logger.debug("threadPoolExecutor need shutdown|${outQueueFLevel.size}|${outQueueSLevel.size}|" +
                        "${errQueueFLevel.size}|${errQueueSLevel.size}")
                try {
                    Thread.sleep(50)
                } catch (e: InterruptedException) {
                    break
                }
                if (outQueueFLevel.isEmpty() &&
                        outQueueSLevel.isEmpty() &&
                        errQueueFLevel.isEmpty() &&
                        errQueueSLevel.isEmpty()) {
                    break
                }
            }
        }
        thread.start()
        kotlin.runCatching {
            thread.join(300_000) // 等待5分钟
            context.close = true
            if (thread.isAlive) {
                logger.debug("Operation timed out")
                thread.interrupt() // 中断线程
                logger.debug("Operation timed out shutdownNow successfully")
            } else {
                logger.debug("Streams stopped successfully")
            }
        }
    }

    private fun dealWithStdout(prefix: String,
                               print2Logger: Boolean,
                               executor: CommandLineExecutor,
                               contextLogFile: String?,
                               context: TaskRunContext) {
        val taskF = Runnable {
            while (true) {
                if (context.close) break
                val line: String? = outQueueFLevel.peek()
                if (line != null) {
                    /*补齐前缀*/
                    val tmpLine: String = prefix + line
                    println(tmpLine)
                    outQueueSLevel.add(tmpLine)
                    outQueueFLevel.poll()
                } else {
                    try {
                        Thread.sleep(10)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
        }
        context.outTaskFLevel = taskF
        threadPoolExecutor.execute(taskF)
        val taskS = Runnable {
            while (true) {
                if (context.close) break
                val line: String? = outQueueSLevel.poll()
                if (line != null) {
                    if (print2Logger) {
                        /*提取特殊内容到文件进行持久化存储并输出到上下文*/
                        appendResultToFile(executor.workingDirectory, contextLogFile, line)
                    }
                } else {
                    try {
                        Thread.sleep(10)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
        }
        context.outTaskSLevel = taskS
        threadPoolExecutor.execute(taskS)
    }

    private fun dealWithStderr(prefix: String,
                               print2Logger: Boolean,
                               executor: CommandLineExecutor,
                               contextLogFile: String?,
                               context: TaskRunContext,
                               errString: EvictingQueue<Char>
    ) {
        val taskF = Runnable {
            while (true) {
                if (context.close) break
                val line: String? = errQueueFLevel.peek()
                if (line != null) {
                    /*补齐前缀*/
                    val tmpLine: String = prefix + line
                    logger.error(tmpLine)
                    errString.addAll(tmpLine.toList())
                    errString.add('\n')
                    errQueueSLevel.add(tmpLine)
                    errQueueFLevel.poll()
                } else {
                    try {
                        Thread.sleep(10)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
        }
        context.errTaskFLevel = taskF
        threadPoolExecutor.execute(taskF)
        val taskS = Runnable {
            while (true) {
                if (context.close) break
                val line: String? = errQueueSLevel.poll()
                if (line != null) {
                    if (print2Logger) {
                        /*提取特殊内容到文件进行持久化存储并输出到上下文*/
                        appendResultToFile(executor.workingDirectory, contextLogFile, line)
                    }
                } else {
                    try {
                        Thread.sleep(10)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
        }
        context.errTaskSLevel = taskS
        threadPoolExecutor.execute(taskS)
    }

    private fun adjusterTask(context: TaskRunContext) {
        // 动态调整线程池大小的任务
        val adjusterTask = Runnable {
            while (true) {
                if (context.close) break
                if (context.outTaskSLevelInit()) {
                    adjuster(context.outTaskSLevel, outQueueSLevel)
                }
                if (context.errTaskSLevelInit()) {
                    adjuster(context.errTaskSLevel, errQueueSLevel)
                }
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }

        // 向线程池提交调整任务
        threadPoolExecutor.execute(adjusterTask)
    }

    private fun adjuster(task: Runnable, queue: Queue<String>) {
        val queueSize = queue.size
        if (queueSize > 100 * threadPoolExecutor.poolSize && threadPoolExecutor.poolSize < MAXIMUM_POOL_SIZE) {
            logger.debug("Increasing thread pool size|${threadPoolExecutor.poolSize}")
            threadPoolExecutor.execute(task)
        }
    }

    /*写内容到文件*/
    private fun appendResultToFile(
            workspace: File?,
            resultLogFile: String?,
            tmpLine: String
    ) {
        /*写入红线指标信息*/
        parseGate(tmpLine)?.let {
            File(workspace, ScriptEnvUtils.getQualityGatewayEnvFile()).appendText(it + "\n")
        }

        if (resultLogFile == null) {
            return
        }
        /*写variable到文件*/
        parseVariable(tmpLine)?.let {
            File(workspace, resultLogFile).appendText(it + "\n")
        }
        /*写output到文件*/
        parseOutput(tmpLine)?.let {
            File(workspace, resultLogFile).appendText(it + "\n")
        }
        /*写output到文件*/
        appendRemarkToFile(tmpLine)?.let {
            File(workspace, resultLogFile).appendText(it + "\n")
        }
    }

    /*解析variable格式的变量*/
    fun parseVariable(
            tmpLine: String
    ): String? {
        /*相应匹配规则*/
        val pattenVar = "[\"]?::set-variable\\sname=.*"
        val prefixVar = "::set-variable name="
        if (Pattern.matches(pattenVar, tmpLine)) {
            /*正则匹配后做拆分处理*/
            val value = tmpLine.removeSurrounding("\"").removePrefix(prefixVar)
            val keyValue = value.split("::")
            if (keyValue.size >= 2) {
                return "variables.${keyValue[0]}=${value.removePrefix("${keyValue[0]}::")}"
            }
        }
        return null
    }

    fun parseOutput(
            tmpLine: String
    ): String? {
        /*相应匹配规则*/
        val pattenOutput = "[\"]?::set-output\\s(.*)"
        val prefixOutput = "::set-output "
        if (Pattern.matches(pattenOutput, tmpLine)) {
            /*正则匹配后做拆分处理*/
            val value = tmpLine.removeSurrounding("\"").removePrefix(prefixOutput)

            val keyValue = value.split("::")
            if (keyValue.size < 2) return null
            val key = keyValue[0]

            val nameMatcher = getOutputMarcher(OUTPUT_NAME.matcher(key)) ?: ""
            val typeMatcher = getOutputMarcher(OUTPUT_TYPE.matcher(key)) ?: "string" // type 默认为string
            val labelMatcher = getOutputMarcher(OUTPUT_LABEL.matcher(key)) ?: ""
            val pathMatcher = getOutputMarcher(OUTPUT_PATH.matcher(key)) ?: ""
            val reportTypeMatcher = getOutputMarcher(OUTPUT_REPORT_TYPE.matcher(key)) ?: ""
            // 以逗号为分隔符 左右依次为name type label path reportType
            return "$nameMatcher," +
                    "$typeMatcher," +
                    "$labelMatcher," +
                    "$pathMatcher," +
                    "$reportTypeMatcher=${value.removePrefix("${keyValue[0]}::")}"
        }
        return null
    }

    private fun appendRemarkToFile(
            tmpLine: String
    ): String? {
        val pattenVar = "[\"]?::set-remark\\s.*"
        val prefixVar = "::set-remark "
        if (Pattern.matches(pattenVar, tmpLine)) {
            val value = tmpLine.removeSurrounding("\"").removePrefix(prefixVar)
            return "BK_CI_BUILD_REMARK=$value"
        }
        return null
    }

    fun parseGate(
            tmpLine: String
    ): String? {
        /*相应匹配规则*/
        val pattenOutput = "[\"]?::set-gate-value\\s(.*)"
        val prefixOutput = "::set-gate-value "
        if (Pattern.matches(pattenOutput, tmpLine)) {
            /*正则匹配后做拆分处理*/
            val value = tmpLine.removeSurrounding("\"").removePrefix(prefixOutput)
            val name = getOutputMarcher(OUTPUT_NAME.matcher(value))
            val title = getOutputMarcher(OUTPUT_GATE_TITLE.matcher(value))
            /*对2种类型的标志位分别存储，互不干扰*/
            val keyValue = value.split("::")
            if (keyValue.size >= 2) {
                // pass_rate=1,pass_rate=通过率\n
                var text = "$name=${value.removePrefix("${keyValue[0]}::")}"
                if (!title.isNullOrBlank()) {
                    text = text.plus(",$name=$title")
                }
                return text
            }
        }
        return null
    }

    fun getOutputMarcher(macher: Matcher): String? {
        return with(macher) {
            /*只返回匹配到的第一个，否则返回null*/
            if (this.find()) {
                this.group(1)
            } else null
        }
    }
}
