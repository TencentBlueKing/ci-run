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

import com.tencent.bk.devops.atom.enums.CharsetType
import com.tencent.bk.devops.atom.utils.CommandLineUtils
import com.tencent.bk.devops.atom.utils.CommonUtil
import com.tencent.bk.devops.atom.utils.ScriptEnvUtils
import org.apache.commons.exec.CommandLine
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files

object PwshUtil {

    // 
    private const val setEnv = "function setEnv(\$key, \$value)\n" +
        "{\n" +
        "    Set-Item -Path Env:\\\$key -Value \$value\n" +
        "    \"\$key=\$value\" | Out-File -Append ##resultFile##\n" +
        "}\n"

    //
    private const val setGateValue = "function setGateValue(\$key, \$value)\n" +
        "{\n" +
        "    \"\$key=\$value\" | Out-File -Append ##gateValueFile##\n" +
        "}\n"

    private val logger = LoggerFactory.getLogger(PwshUtil::class.java)

    // 2021-06-11 batchScript需要过滤掉上下文产生的变量，防止注入到环境变量中
    private val specialKey = listOf("variables.", "settings.", "envs.", "ci.", "job.", "jobs.", "steps.")

    private val specialValue = listOf("\n", "\r")

    @Suppress("ALL")
    fun execute(
        script: String,
        buildId: String,
        runtimeVariables: Map<String, String>,
        dir: File,
        prefix: String = "",
        paramClassName: List<String>,
        errorMessage: String? = null,
        workspace: File = dir,
        print2Logger: Boolean = true,
        charsetType: CharsetType? = null
    ): String {
        try {
            val file = getCommandFile(
                buildId = buildId,
                script = script,
                runtimeVariables = runtimeVariables,
                dir = dir,
                workspace = workspace,
                charsetType = charsetType,
                paramClassName = paramClassName
            )
            val command = "pwsh \"${file.canonicalPath}\""
            return CommandLineUtils.execute(
                cmdLine = CommandLine.parse(command),
                workspace = dir,
                print2Logger = print2Logger,
                prefix = prefix,
                executeErrorMessage = "",
                buildId = buildId,
                charSetType = charsetType
            )
        } catch (ignore: Throwable) {
            val errorInfo = errorMessage ?: "Fail to execute bat script"
            logger.warn(errorInfo, ignore)
            throw ignore
        }
    }

    @Suppress("ALL")
    fun getCommandFile(
        buildId: String,
        script: String,
        runtimeVariables: Map<String, String>,
        dir: File,
        workspace: File = dir,
        paramClassName: List<String>,
        charsetType: CharsetType? = null
    ): File {
        val file = Files.createTempFile(CommonUtil.getTmpDir(), "devops_script", ".ps1").toFile()
        file.deleteOnExit()

        val command = StringBuilder()

        command.append("Set-Item -Path Env:\\WORKSPACE -Value '${workspace.absolutePath}'\n")
            .append("Set-Item -Path Env:\\DEVOPS_BUILD_SCRIPT_FILE -Value '${file.absolutePath}'\n")
            .append("\r\n")

        runtimeVariables
//            .plus(CommonEnv.getCommonEnv()) //
            .filterNot { specialEnv(it.key, it.value) || it.key in paramClassName }
            .forEach { (name, value) ->
                command.append("Set-Item -Path Env:\\$name -Value '$value'\n")
            }

        val charset = when (charsetType) {
            CharsetType.UTF_8 -> Charsets.UTF_8
            CharsetType.GBK -> Charset.forName(CharsetType.GBK.name)
            else -> Charset.defaultCharset()
        }
        logger.info("The default charset is $charset")

        if (charset == Charsets.UTF_8) {
            command.append("[Console]::OutputEncoding = [System.Text.Encoding]::UTF8\r\n")
        }

        command.append(
            setEnv.replace(
                oldValue = "##resultFile##",
                newValue = File(dir, ScriptEnvUtils.getEnvFile(buildId)).absolutePath
            )
        )
            .append(
                setGateValue.replace(
                    oldValue = "##gateValueFile##",
                    newValue = File(dir, ScriptEnvUtils.getQualityGatewayEnvFile()).canonicalPath
                )
            )
            .append(script.replace("\n", "\r\n"))
            .append("\r\n")
            .append("exit")

        file.writeText(command.toString(), charset)
        CommonUtil.printTempFileInfo(file, charset)
        return file
    }

    private fun specialEnv(key: String, value: String): Boolean {
        var match = false
        /*过滤处理特殊的key*/
        for (it in specialKey) {
            if (key.trim().startsWith(it)) {
                match = true
                break
            }
        }

        /*过滤处理特殊的value*/
        for (it in specialValue) {
            if (value.contains(it)) {
                match = true
                break
            }
        }
        return match
    }
}
