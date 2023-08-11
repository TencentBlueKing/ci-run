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
import com.tencent.bk.devops.atom.enums.OSType
import com.tencent.bk.devops.atom.exception.AtomException
import com.tencent.bk.devops.atom.pojo.AgentEnv
import com.tencent.bk.devops.atom.pojo.BuildEnv
import com.tencent.bk.devops.atom.utils.CommandLineUtils
import com.tencent.bk.devops.atom.utils.CommonUtil
import com.tencent.bk.devops.atom.utils.ScriptEnvUtils
import com.tencent.bk.devops.atom.utils.getEnvironmentPathPrefix
import org.apache.commons.exec.CommandLine
import org.apache.commons.text.StringEscapeUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files

@Suppress("ALL")
object PythonUtil {

    //
    private const val setEnv = "def setEnv(key,value):\n" +
        "    os.environ[key]=value\n" +
        "    f = open(\"##resultFile##\", 'a+')\n" +
        "    print(\"{0}={1}\".format(key, value), file=f)\n"
    private const val format_multiple_lines = "def format_multiple_lines(s: str):\n" +
        "    out = s.replace('%','%25').replace('\\n','%0A').replace('\\r','%0D')\n" +
        "    f = open(\"##resultFile##\", 'a+')\n" +
        "    print(out, file=f)\n" +
        "    return out\n"
    //
//    private const val setGateValue = "setGateValue(){\n" +
//        "        local key=\$1\n" +
//        "        local val=\$2\n" +
//        "\n" +
//        "        if [[ -z \"\$@\" ]]; then\n" +
//        "            return 0\n" +
//        "        fi\n" +
//        "\n" +
//        "        if ! echo \"\$key\" | grep -qE \"^[a-zA-Z_][a-zA-Z0-9_]*\$\"; then\n" +
//        "            echo \"[\$key] is invalid\" >&2\n" +
//        "            return 1\n" +
//        "        fi\n" +
//        "\n" +
//        "        echo \$key=\$val  >> ##gateValueFile##\n" +
//        "    }\n"

//    lateinit var buildEnvs: List<BuildEnv>

    private val specialKey = listOf(".", "-")

    //    private val specialValue = listOf("|", "&", "(", ")")
    private val specialCharToReplace = Regex("['\n]") // --bug=75509999 Agent环境变量中替换掉破坏性字符
    private val logger = LoggerFactory.getLogger(PythonUtil::class.java)

    fun execute(
        buildId: String,
        script: String,
        dir: File,
        buildEnvs: List<BuildEnv>,
        runtimeVariables: Map<String, String>,
        continueNoneZero: Boolean = false,
        systemEnvVariables: Map<String, String>? = null,
        prefix: String = "",
        errorMessage: String? = null,
        workspace: File = dir,
        print2Logger: Boolean = true,
        stepId: String? = null,
        paramClassName: List<String>,
        charsetType: CharsetType? = null
    ): String {
        return executeUnixCommand(
            command = "python3 " + getCommandFile(
                buildId = buildId,
                script = script,
                dir = dir,
                workspace = workspace,
                buildEnvs = buildEnvs,
                runtimeVariables = runtimeVariables,
                continueNoneZero = continueNoneZero,
                systemEnvVariables = systemEnvVariables,
                paramClassName = paramClassName,
                charsetType = charsetType
            ).canonicalPath,
            sourceDir = dir,
            prefix = prefix,
            errorMessage = errorMessage,
            print2Logger = print2Logger,
            executeErrorMessage = "",
            buildId = buildId,
            stepId = stepId,
            charsetType = charsetType
        )
    }

    fun getCommandFile(
        buildId: String,
        script: String,
        dir: File,
        buildEnvs: List<BuildEnv>,
        runtimeVariables: Map<String, String>,
        continueNoneZero: Boolean = false,
        systemEnvVariables: Map<String, String>? = null,
        workspace: File = dir,
        charSetType: CharsetType = CharsetType.UTF_8,
        paramClassName: List<String>,
        charsetType: CharsetType? = null
    ): File {
        val file = Files.createTempFile(CommonUtil.getTmpDir(), "devops_script", ".py").toFile()
        file.deleteOnExit()

        val command = StringBuilder()

        command.append("import os\n")
            .append("os.environ['WORKSPACE']=\"${StringEscapeUtils.escapeJava(workspace.absolutePath)}\"\n")
            .append("os.environ['DEVOPS_BUILD_SCRIPT_FILE']=\"${StringEscapeUtils.escapeJava(file.absolutePath)}\"\n")

        // 设置系统环境变量
        systemEnvVariables?.forEach { (name, value) ->
            command.append("os.environ['$name']=\"${StringEscapeUtils.escapeJava(value)}\"\n")
        }

        val commonEnv = runtimeVariables
            .filterNot { specialEnv(it.key) || it.key in paramClassName }
        if (commonEnv.isNotEmpty()) {
            commonEnv.forEach { (name, value) ->
                command.append("os.environ['$name']=\"${CommonUtil.escapeString(value)}\"\n")
            }
        }
        if (buildEnvs.isNotEmpty()) {
            var path = ""
            buildEnvs.forEach { buildEnv ->
                val home = File(getEnvironmentPathPrefix(), "${buildEnv.name}/${buildEnv.version}/")
                if (!home.exists()) {
                    logger.error("The environment variable path (${home.absolutePath}) does not exist")
                }
                val envFile = File(home, buildEnv.binPath)
                if (!envFile.exists()) {
                    logger.error("The environment variable path (${envFile.absolutePath}) does not exist")
                    return@forEach
                }
                // command.append("export $name=$path")
                path = if (path.isEmpty()) {
                    envFile.absolutePath
                } else {
                    "$path:${envFile.absolutePath}"
                }
                if (buildEnv.env.isNotEmpty()) {
                    buildEnv.env.forEach { (name, path) ->
                        val p = File(home, path)
                        command.append("$name='${p.absolutePath}'\n")
                    }
                }
            }
            if (path.isNotEmpty()) {
                path = "$path:\$PATH"
                command.append("os.environ['PATH']=\"${StringEscapeUtils.escapeJava(path)}\"\n")
            }
        }

        command.append(
            setEnv.replace(
                oldValue = "##resultFile##",
                newValue = File(dir, ScriptEnvUtils.getEnvFile(buildId)).absolutePath.replace("\\", "/")
            )
        )

        command.append(
            format_multiple_lines.replace(
                oldValue = "##resultFile##",
                newValue = File(dir, ScriptEnvUtils.getMultipleLineFile(buildId)).absolutePath.replace("\\", "/")
            )
        )
//        command.append(
//            setGateValue.replace(oldValue = "##gateValueFile##",
//            newValue = File(dir, ScriptEnvUtils.getQualityGatewayEnvFile()).absolutePath))
        command.append(script)

        val charset = when (charSetType) {
            CharsetType.UTF_8 -> Charsets.UTF_8
            CharsetType.GBK -> Charset.forName(CharsetType.GBK.name)
            else -> Charset.defaultCharset()
        }
        logger.info("The default charset is $charset")

        file.writeText(command.toString(), charset)

        if (AgentEnv.getOS() != OSType.WINDOWS) {
            executeUnixCommand(
                command = "chmod +x ${file.absolutePath}",
                sourceDir = dir,
                buildId = buildId,
                charsetType = charsetType
            )
        }
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
        buildId: String,
        stepId: String? = null,
        charsetType: CharsetType? = null
    ): String {
        try {
            return CommandLineUtils.execute(
                cmdLine = CommandLine.parse(command),
                workspace = sourceDir,
                print2Logger = print2Logger,
                prefix = prefix,
                executeErrorMessage = executeErrorMessage,
                buildId = buildId,
                charSetType = charsetType
            )
        } catch (taskError: AtomException) {
            throw taskError
        } catch (ignored: Throwable) {
            val errorInfo = errorMessage ?: "Fail to run the command $command"
            logger.info("$errorInfo because of error(${ignored.message})")
            throw AtomException(
                ignored.message ?: ""
            )
        }
    }

    /*过滤处理特殊的key*/
    private fun specialEnv(key: String): Boolean {
        return specialKey.any { key.contains(it) }
    }
}
