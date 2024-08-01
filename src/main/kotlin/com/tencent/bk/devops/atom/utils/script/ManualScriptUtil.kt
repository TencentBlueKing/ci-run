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

package com.tencent.bk.devops.atom.utils.script

import com.tencent.bk.devops.atom.common.ErrorCode
import com.tencent.bk.devops.atom.enums.CharsetType
import com.tencent.bk.devops.atom.utils.CommandLineUtils
import com.tencent.bk.devops.atom.utils.CommonUtil
import com.tencent.bk.devops.plugin.exception.TaskExecuteException
import com.tencent.bk.devops.plugin.pojo.ErrorType
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.regex.Pattern
import org.apache.commons.exec.CommandLine
import org.slf4j.LoggerFactory

@Suppress("ALL")
object ManualScriptUtil {

    private val specialKey = listOf(".", "-")

    private val filePathRegex = Pattern.compile("(\\./random_name\\.[a-zA-Z0-9]*)")
    private val logger = LoggerFactory.getLogger(ManualScriptUtil::class.java)

    fun execute(
        buildId: String,
        script: String,
        startCommand: String,
        dir: File,
        prefix: String = "",
        errorMessage: String? = null,
        print2Logger: Boolean = true
    ): String {
        val filePath = CommandLineUtils.getOutputMarcher(filePathRegex.matcher(startCommand))
        if (filePath.isNullOrBlank()) {
            logger.info("Please check whether your input meets the required format: [$startCommand]")
            throw TaskExecuteException(
                errorType = ErrorType.USER,
                errorCode = ErrorCode.USER_SCRIPT_COMMAND_INVAILD,
                errorMsg = "Please check whether your input meets the required format: [$startCommand]"
            )
        }
        return executeUnixCommand(
            command = startCommand.replace(
                filePath, getCommandFile(
                    buildId = buildId,
                    script = script,
                    dir = dir,
                    filePath = filePath
                ).canonicalPath
            ),
            sourceDir = dir,
            prefix = prefix,
            errorMessage = errorMessage,
            print2Logger = print2Logger,
            executeErrorMessage = "",
            buildId = buildId
        )
    }

    private fun getCommandFile(
        buildId: String,
        script: String,
        dir: File,
        filePath: String,
        charSetType: CharsetType = CharsetType.UTF_8
    ): File {
        val file = Files.createTempFile(CommonUtil.getTmpDir(), "devops_script", filePath).toFile()
        file.deleteOnExit()

        val charset = when (charSetType) {
            CharsetType.UTF_8 -> Charsets.UTF_8
            CharsetType.GBK -> Charset.forName(CharsetType.GBK.name)
            else -> Charset.defaultCharset()
        }
        logger.info("The default charset is $charset")

        file.writeText(script, charset)

        executeUnixCommand(
            command = "chmod +x ${file.absolutePath}",
            sourceDir = dir,
            buildId = buildId
        )
        CommonUtil.printTempFileInfo(file, charset)
        return file
    }

    private fun executeUnixCommand(
        command: String,
        sourceDir: File,
        prefix: String = "",
        errorMessage: String? = null,
        print2Logger: Boolean = true,
        executeErrorMessage: String? = null,
        buildId: String
    ): String {
        try {
            return CommandLineUtils.execute(
                cmdLine = CommandLine.parse(command),
                workspace = sourceDir,
                print2Logger = print2Logger,
                prefix = prefix,
                executeErrorMessage = executeErrorMessage,
                buildId = buildId
            )
        } catch (taskError: TaskExecuteException) {
            throw taskError
        } catch (ignored: Throwable) {
            val errorInfo = errorMessage ?: "Fail to run the command $command"
            logger.info("$errorInfo because of error(${ignored.message})")
            throw TaskExecuteException(
                errorType = ErrorType.USER,
                errorCode = ErrorCode.USER_SCRIPT_COMMAND_INVAILD,
                errorMsg = ignored.message ?: ""
            )
        }
    }

    /*过滤处理特殊的key*/
    private fun specialEnv(key: String): Boolean {
        return specialKey.any { key.contains(it) }
    }
}
