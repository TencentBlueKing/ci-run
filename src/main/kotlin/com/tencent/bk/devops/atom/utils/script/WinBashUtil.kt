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
import com.tencent.bk.devops.atom.exception.AtomException
import com.tencent.bk.devops.atom.pojo.BuildEnv
import com.tencent.bk.devops.atom.utils.CommandLineUtils
import com.tencent.bk.devops.atom.utils.CommonUtil
import com.tencent.bk.devops.atom.utils.ScriptEnvUtils
import org.apache.commons.exec.CommandLine
import org.apache.commons.io.FilenameUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files

@Suppress("LongParameterList")
object WinBashUtil {

    private const val DEFAULT_GIT_BASH_PATH = "C:\\Program Files\\Git\\bin\\bash.exe"

    //
    private const val setEnv =
        "setEnv(){\n" +
            "        local key=\$1\n" +
            "        local val=\$2\n" +
            "\n" +
            "        if [[ -z \"\$@\" ]]; then\n" +
            "            return 0\n" +
            "        fi\n" +
            "\n" +
            "        if ! echo \"\$key\" | grep -qE \"^[a-zA-Z_][a-zA-Z0-9_]*\$\"; then\n" +
            "            echo \"[\$key] is invalid\" >&2\n" +
            "            return 1\n" +
            "        fi\n" +
            "\n" +
            "        echo \$key=\$val  >> ##resultFile##\n" +
            "        export \$key=\"\$val\"\n" +
            "    }\n"
    private const val format_multiple_lines =
        "format_multiple_lines() {\n" +
            "    local content=\$1\n" +
            "    content=\"\${content//'%'/'%25'}\"\n" +
            "    content=\"\${content//\$'\\n'/'%0A'}\"\n" +
            "    content=\"\${content//\$'\\r'/'%0D'}\"\n" +
            "    /bin/echo \"\$content\"|sed 's/\\\\n/%0A/g'|sed 's/\\\\r/%0D/g' >> ##resultFile##\n" +
            "}\n"
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
    private val logger = LoggerFactory.getLogger(BashUtil::class.java)

    fun execute(
        buildId: String,
        script: String,
        dir: File,
        runtimeVariables: Map<String, String>,
        continueNoneZero: Boolean = false,
        systemEnvVariables: Map<String, String>? = null,
        prefix: String = "",
        errorMessage: String? = null,
        workspace: File = dir,
        print2Logger: Boolean = true,
        paramClassName: List<String>,
        charSetType: CharsetType? = null
    ): String {
        val gitBashFile = File(DEFAULT_GIT_BASH_PATH)
        val scriptPath = getCommandFile(
            buildId = buildId,
            script = script,
            dir = dir,
            runtimeVariables = runtimeVariables,
            continueNoneZero = continueNoneZero,
            systemEnvVariables = systemEnvVariables,
            workspace = workspace,
            paramClassName = paramClassName,
            charSetType = charSetType
        ).canonicalPath
        val command = if (gitBashFile.exists()) {
            arrayOf("/c", "\"\"$DEFAULT_GIT_BASH_PATH\" --login -i -- $scriptPath\"")
        } else {
            logger.warn("git-bash was not found in the default installation location, " +
                "please add %YOUR_GIT_PATH%\\bin to the system environment variable path.")
            arrayOf("/c", "\"bash --login -i -- $scriptPath\"")
        }

        return executeUnixCommand(
            cmdLine = CommandLine("cmd.exe").addArguments(command, false),
            sourceDir = dir,
            prefix = prefix,
            errorMessage = errorMessage,
            print2Logger = print2Logger,
            executeErrorMessage = "",
            buildId = buildId,
            charSetType = charSetType
        )
    }

    fun getCommandFile(
        buildId: String,
        script: String,
        dir: File,
        runtimeVariables: Map<String, String>,
        continueNoneZero: Boolean = false,
        systemEnvVariables: Map<String, String>? = null,
        workspace: File = dir,
        charSetType: CharsetType? = CharsetType.UTF_8,
        paramClassName: List<String>
    ): File {
        val file = Files.createTempFile(CommonUtil.getTmpDir(), "devops_script", ".sh").toFile()
        file.deleteOnExit()

        val command = StringBuilder()
        val bashStr = script.split("\n")[0]
        if (bashStr.startsWith("#!/")) {
            command.append(bashStr).append("\n")
        }

        command.append("export WORKSPACE=${FilenameUtils.separatorsToUnix(workspace.absolutePath)}\n")
            .append("export DEVOPS_BUILD_SCRIPT_FILE=${FilenameUtils.separatorsToUnix(file.absolutePath)}\n")

        // 设置系统环境变量
        systemEnvVariables?.forEach { (name, value) ->
            command.append("export $name=$value\n")
        }

        val commonEnv = runtimeVariables.filterNot { specialEnv(it.key) || it.key in paramClassName }
        if (commonEnv.isNotEmpty()) {
            commonEnv.forEach { (name, value) ->
                // --bug=75509999 Agent环境变量中替换掉破坏性字符
                val clean = value.replace(specialCharToReplace, "")
                command.append("export $name='$clean'\n")
            }
        }
        // not use
        /*if (buildEnvs.isNotEmpty()) {
            var path = ""
            buildEnvs.forEach { buildEnv ->
                val home = File(getEnvironmentPathPrefix(), "${buildEnv.name}/${buildEnv.version}/")
                if (!home.exists()) {
                    logger.error("环境变量路径(${home.absolutePath})不存在")
                }
                val envFile = File(home, buildEnv.binPath)
                if (!envFile.exists()) {
                    logger.error("环境变量路径(${envFile.absolutePath})不存在")
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
                        command.append("export $name=${p.absolutePath}\n")
                    }
                }
            }
            if (path.isNotEmpty()) {
                path = "$path:\$PATH"
                command.append("export PATH=$path\n")
            }
        }*/

        if (!continueNoneZero) {
            command.append("set -e\n")
        } else {
            logger.info("每行命令运行返回值非零时，继续执行脚本")
            command.append("set +e\n")
        }

        command.append(
            setEnv.replace(
                oldValue = "##resultFile##",
                newValue = FilenameUtils.separatorsToUnix(File(dir, ScriptEnvUtils.getEnvFile(buildId)).absolutePath)
            )
        )

        command.append(
            format_multiple_lines.replace(
                oldValue = "##resultFile##", newValue = FilenameUtils.separatorsToUnix(
                    File(
                        dir, ScriptEnvUtils.getMultipleLineFile(buildId)
                    ).absolutePath
                )
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

        CommonUtil.printTempFileInfo(file, charset)
        return file
    }

    private fun executeUnixCommand(
        cmdLine: CommandLine,
        sourceDir: File,
        prefix: String = "",
        errorMessage: String? = null,
        print2Logger: Boolean = true,
        executeErrorMessage: String? = null,
        buildId: String,
        charSetType: CharsetType? = null
    ): String {
        try {
            return CommandLineUtils.execute(
                cmdLine = cmdLine,
                workspace = sourceDir,
                print2Logger = print2Logger,
                prefix = prefix,
                executeErrorMessage = executeErrorMessage,
                buildId = buildId,
                charSetType = charSetType
            )
        } catch (taskError: AtomException) {
            throw taskError
        } catch (ignored: Throwable) {
            val errorInfo = errorMessage ?: "Fail to run the command "
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
