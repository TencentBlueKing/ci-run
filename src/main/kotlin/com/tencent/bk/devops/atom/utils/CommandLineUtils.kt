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

import com.tencent.bk.devops.atom.enums.CharsetType
import com.tencent.bk.devops.atom.exception.AtomException
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.LogOutputStream
import org.apache.commons.exec.PumpStreamHandler
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.util.regex.Matcher
import java.util.regex.Pattern

object CommandLineUtils {

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

    fun execute(
        cmdLine: CommandLine,
        workspace: File?,
        print2Logger: Boolean,
        prefix: String = "",
        executeErrorMessage: String? = null,
        buildId: String,
        stepId: String? = null,
        charSetType: CharsetType? = null
    ): String {
        /*result 用于装载返回信息*/
        val result = StringBuilder()
        logger.debug("will execute command >>> $cmdLine")

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
        val outputStream = object : LogOutputStream() {
            override fun processBuffer() {
                val privateStringField = LogOutputStream::class.java.getDeclaredField("buffer")
                privateStringField.isAccessible = true
                /*反射拿到buffer 解决字符集编码问题*/
                val buffer = privateStringField.get(this) as ByteArrayOutputStream
                processLine(buffer.toString(charset))
                /*手动reset*/
                buffer.reset()
            }

            override fun processLine(line: String?, level: Int) {
                if (line == null)
                    return

                /*补齐前缀*/
                var tmpLine: String = prefix + line

                lineParser.forEach {
                    /*做日志脱敏*/
                    tmpLine = it.onParseLine(tmpLine)
                }
                if (print2Logger) {
                    /*提取特殊内容到文件进行持久化存储并输出到上下文*/
                    appendResultToFile(executor.workingDirectory, contextLogFile, tmpLine)
                }
                println(tmpLine)
                /*装载result*/
                result.append(tmpLine).append("\n")
            }
        }
        /*定义error输出流*/
        val errStream = object : LogOutputStream() {
            override fun processBuffer() {
                val privateStringField = LogOutputStream::class.java.getDeclaredField("buffer")
                privateStringField.isAccessible = true
                /*反射拿到buffer 解决字符集编码问题*/
                val buffer = privateStringField.get(this) as ByteArrayOutputStream
                processLine(buffer.toString(charset))
                /*手动reset*/
                buffer.reset()
            }

            override fun processLine(line: String?, level: Int) {
                if (line == null)
                    return

                /*补齐前缀*/
                var tmpLine: String = prefix + line

                lineParser.forEach {
                    /*做日志脱敏*/
                    tmpLine = it.onParseLine(tmpLine)
                }
                if (print2Logger) {
                    /*提取特殊内容到文件进行持久化存储并输出到上下文*/
                    appendResultToFile(executor.workingDirectory, contextLogFile, tmpLine)
                }
                logger.error(tmpLine)
                /*装载result*/
                result.append(tmpLine).append("\n")
            }
        }
        /*定义好输出流*/
        executor.streamHandler = PumpStreamHandler(outputStream, errStream)
        try {
            /*执行脚本*/
            val exitCode = executor.execute(cmdLine)
            if (exitCode != 0) {
                /*执行返回码，非零表示执行出错，这时直接抛错。为用户自己的脚本问题*/
                throw AtomException(
                    "$prefix Script command execution failed with exit code($exitCode)"
                )
            }
        } catch (ignored: Throwable) {
            /*对其余异常兜底处理，可能是执行脚本时抛错的错。*/
            val errorMessage = executeErrorMessage ?: "Fail to execute the command"
            logger.warn(errorMessage)
            throw AtomException(
                ignored.message ?: ""
            )
        }
        return result.toString()
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

            val nameMatcher = getOutputMarcher(OUTPUT_NAME.matcher(value)) ?: ""
            val typeMatcher = getOutputMarcher(OUTPUT_TYPE.matcher(value)) ?: "string" // type 默认为string
            val labelMatcher = getOutputMarcher(OUTPUT_LABEL.matcher(value)) ?: ""
            val pathMatcher = getOutputMarcher(OUTPUT_PATH.matcher(value)) ?: ""
            val reportTypeMatcher = getOutputMarcher(OUTPUT_REPORT_TYPE.matcher(value)) ?: ""

            /*对5种类型的标志位分别存储，互不干扰*/
            val keyValue = value.split("::")
            if (keyValue.size >= 2) {
                // 以逗号为分隔符 左右依次为name type label path reportType
                return "$nameMatcher," +
                    "$typeMatcher," +
                    "$labelMatcher," +
                    "$pathMatcher," +
                    "$reportTypeMatcher=${value.removePrefix("${keyValue[0]}::")}"
            }
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

    private fun getOutputMarcher(macher: Matcher): String? {
        return with(macher) {
            /*只返回匹配到的第一个，否则返回null*/
            if (this.find()) {
                this.group(1)
            } else null
        }
    }
}
