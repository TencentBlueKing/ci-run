package com.tencent.bk.devops.atom

import com.tencent.bk.devops.atom.api.QualityApi
import com.tencent.bk.devops.atom.common.CI_TOKEN_CONTEXT
import com.tencent.bk.devops.atom.common.ErrorCode
import com.tencent.bk.devops.atom.common.JOB_OS_CONTEXT
import com.tencent.bk.devops.atom.common.Status
import com.tencent.bk.devops.atom.common.WORKSPACE_CONTEXT
import com.tencent.bk.devops.atom.common.utils.ReplacementUtils
import com.tencent.bk.devops.atom.enums.CharsetType
import com.tencent.bk.devops.atom.enums.OSType
import com.tencent.bk.devops.atom.exception.AtomException
import com.tencent.bk.devops.atom.pojo.AdditionalOptions
import com.tencent.bk.devops.atom.pojo.AgentEnv.getOS
import com.tencent.bk.devops.atom.pojo.ArtifactData
import com.tencent.bk.devops.atom.pojo.AtomResult
import com.tencent.bk.devops.atom.pojo.ReportData
import com.tencent.bk.devops.atom.pojo.ScriptRunAtomParam
import com.tencent.bk.devops.atom.pojo.ShellType
import com.tencent.bk.devops.atom.pojo.StringData
import com.tencent.bk.devops.atom.pojo.request.IndicatorCreate
import com.tencent.bk.devops.atom.pojo.request.QualityDataType
import com.tencent.bk.devops.atom.spi.AtomService
import com.tencent.bk.devops.atom.spi.TaskAtom
import com.tencent.bk.devops.atom.utils.CommandLineUtils
import com.tencent.bk.devops.atom.utils.I18nUtil
import com.tencent.bk.devops.atom.utils.MessageUtil
import com.tencent.bk.devops.atom.utils.ScriptEnvUtils
import com.tencent.bk.devops.atom.utils.script.BashUtil
import com.tencent.bk.devops.atom.utils.script.BatScriptUtil
import com.tencent.bk.devops.atom.utils.script.PowerShellUtil
import com.tencent.bk.devops.atom.utils.script.PwshUtil
import com.tencent.bk.devops.atom.utils.script.PythonUtil
import com.tencent.bk.devops.atom.utils.script.ShUtil
import com.tencent.bk.devops.atom.utils.script.WinBashUtil
import com.tencent.bk.devops.plugin.pojo.ErrorType
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLDecoder
import java.nio.charset.Charset

/**
 * @version 1.0.0
 */
@AtomService(paramClass = ScriptRunAtomParam::class)
@Suppress("ALL")
class ScriptRunAtom : TaskAtom<ScriptRunAtomParam> {

    private val qualityApi = QualityApi()

    private val USER_ERROR_MESSAGE = """
====== Script Execution Failed, Troubleshooting Guide ======

When the script exit code is non-zero, it indicates that the execution has failed. You can analyze it from the following paths:
  1. Troubleshoot based on error logs.
  2. Manually execute the script locally. If it also fails locally, it is likely to be a script logic issue. 
If it succeeds locally, troubleshoot the build environment (such as environment dependencies or code changes, etc.).
    """

    override fun execute(atomContext: AtomContext<ScriptRunAtomParam>) {

        val param = atomContext.param as ScriptRunAtomParam
        val result = atomContext.result
        // 检查参数
        checkParam(param, result)
        if (result.status != Status.success) {
            return
        }
        val script = param.script
        // 字符集选择，应对某些windows构建存在的字符集不匹配的问题
        val charSetType: CharsetType = try {
            CharsetType.valueOf(param.charSetType)
        } catch (ignore: Throwable) {
            CharsetType.DEFAULT
        }
        val charset = when (charSetType) {
            CharsetType.UTF_8 -> Charsets.UTF_8
            CharsetType.GBK -> Charset.forName(CharsetType.GBK.name)
            else -> Charset.defaultCharset()
        }
        // 获取运行时变量
        val runtimeVariables = atomContext.allParameters.map { it.key to it.value.toString() }.toMap()
        // 获取系统类型
        val osType = getOS()
        // 获取构建id
        val buildId = atomContext.param.pipelineBuildId
        /*拿到工作目录，后续文件操作将在工作目录进行*/
        val workspace = File(param.bkWorkspace)
        /*替换脚本变量，目前变量已在引擎替换，此次预留暂时没有起实际作用*/
        val realCommand = parseTemplate(script, emptyMap(), workspace)
        /*获取input中的变量，为了后续塞环境变量时不会意外将其塞入*/
        val paramClassName = param.javaClass.declaredFields.map { it.name }.toList()

        handle(result) {
            /*获取脚本类型和系统类型*/
            val additionalOptions = AdditionalOptions(param.shell)
            /*检查脚本类型是否适配当前系统*/
            checkOS(additionalOptions.shell, result)
            try {
                when (additionalOptions.shell) {
                    /*batch脚本，windows使用*/
                    ShellType.CMD -> BatScriptUtil.execute(
                        script = realCommand,
                        buildId = buildId,
                        runtimeVariables = runtimeVariables,
                        dir = workspace,
                        paramClassName = paramClassName,
                        charsetType = charSetType
                    )
                    /*bash脚本，windows使用*/
                    ShellType.WIN_BASH -> WinBashUtil.execute(
                        script = realCommand,
                        buildId = buildId,
                        runtimeVariables = runtimeVariables,
                        dir = workspace,
                        paramClassName = paramClassName,
                        charSetType = charSetType
                    )
                    /*shell脚本，一般linux和macos使用*/
                    ShellType.BASH -> BashUtil.execute(
                        script = realCommand,
                        buildId = buildId,
                        runtimeVariables = runtimeVariables,
                        dir = workspace,
                        // 市场插件执行时buildEnvs已经写在环境变量中，作为子进程可以直接读取
                        buildEnvs = emptyList(),
                        stepId = param.stepId,
                        paramClassName = paramClassName
                    )
                    /*python脚本，需要目标构建机安装python3环境*/
                    ShellType.PYTHON -> PythonUtil.execute(
                        script = realCommand,
                        buildId = buildId,
                        runtimeVariables = runtimeVariables,
                        dir = workspace,
                        buildEnvs = emptyList(),
                        stepId = param.stepId,
                        charsetType = charSetType,
                        paramClassName = paramClassName
                    )
                    /*powershell脚本*/
                    ShellType.POWERSHELL_CORE -> PwshUtil.execute(
                        script = realCommand,
                        buildId = buildId,
                        runtimeVariables = runtimeVariables,
                        dir = workspace,
                        paramClassName = paramClassName
                    )
                    /*powershell desktop脚本*/
                    ShellType.POWERSHELL_DESKTOP -> PowerShellUtil.execute(
                        script = realCommand,
                        buildId = buildId,
                        runtimeVariables = runtimeVariables,
                        dir = workspace,
                        paramClassName = paramClassName,
                        charsetType = charSetType
                    )
                    /*执行sh命令的脚本*/
                    ShellType.SH -> ShUtil.execute(
                        script = realCommand,
                        buildId = buildId,
                        runtimeVariables = runtimeVariables,
                        dir = workspace,
                        // 市场插件执行时buildEnvs已经写在环境变量中，作为子进程可以直接读取
                        buildEnvs = emptyList(),
                        stepId = param.stepId,
                        paramClassName = paramClassName
                    )
                    else -> {}
                }

                result.status = Status.success
                result.message = "$osType script executed successfully"
            } catch (taskError: AtomException) {
                /*处理普通异常，这里是脚本逻辑抛出的异常*/
                logger.warn("Fail to run the script task")
                logger.debug("TaskExecuteException|${taskError.message}", taskError)
                result.status = Status.failure
                result.message = "$osType script execution failed"
                val mes = MessageUtil.getMessageByLocale(
                    ErrorCode.USER_ERROR_MESSAGE,
                    I18nUtil.getLanguage(),
                    USER_ERROR_MESSAGE
                )
                /*返回失败以及对应的异常类型*/
                throw AtomException(
                    taskError.message + "\n${URLDecoder.decode(mes, Charsets.UTF_8.name())}"
                )
            } catch (ignore: Throwable) {
                /*处理意外发生的异常，全局捕获*/
                logger.warn("Fail to run the script task")
                logger.debug("Throwable|${ignore.message}", ignore)
                result.status = Status.failure
                result.message = "$osType script execution failed"
                /*返回user类型错误，一般用户使用错误会引起这种情况*/
                val mes = MessageUtil.getMessageByLocale(
                    ErrorCode.USER_ERROR_MESSAGE,
                    I18nUtil.getLanguage(),
                    USER_ERROR_MESSAGE
                )
                throw AtomException(
                    URLDecoder.decode(mes, Charsets.UTF_8.name())
                )
            } finally {
                // 写入上下文
                ScriptEnvUtils.getContext(buildId, workspace).plus(
                    parseContextFromMultiLine(
                        buildId,
                        workspace,
                        charset
                    )
                )
                    .forEach { (key, value) ->
                        /*根据拆分的格式来区分具体的类型*/
                        val split = key.split(",")
                        when (split.size) {
                            /*只有一位，只需要输出为上下文变量格式*/
                            1 -> atomContext.result.data[key] = StringData(value)
                            /*有5位时区分了3种类型情况*/
                            5 ->
                                // 以逗号为分隔符 左右依次为name type label path reportType
                                atomContext.result.data[split[0]] = when (split[1]) {
                                    /*指定string的类型输出为上下文*/
                                    "string" -> StringData(value)
                                    /*指定位artifact的类型输出为构件*/
                                    "artifact" -> ArtifactData(setOf(value))
                                    /*指定为report 输出为报告*/
                                    "report" -> {
                                        /*第三方报告*/
                                        if (split[4].contains("THIRDPARTY")) {
                                            ReportData(split[2], value)
                                            /*本地报告*/
                                        } else if (split[3].isNotBlank()) {
                                            ReportData(split[2], split[3], value)
                                        } else {
                                            /*不支持其他report类型，如果有直接抛错*/
                                            throw AtomException(
                                                "Script execution failed. The set-output report setting is incorrect. Please check"
                                            )
                                        }
                                    }
                                    /*不支持其他set-output类型，如果有直接抛错*/
                                    else -> throw AtomException(
                                        "The script execution failed. The set-output setting is wrong. Please check:$split"
                                    )
                                }
                            /*不支持其他类型，如果有直接抛错*/
                            else -> throw AtomException(
                                "The script failed to execute. The set-output or set-variable settings are incorrect. Please check: [${split.size == 1}]$split"
                            )
                        }
                    }
                // 写入环境变量
                ScriptEnvUtils.getEnv(buildId, workspace).forEach { (key, value) ->
                    atomContext.result.data[key] = StringData(value)
                }
                // 写入质量红线
                setGatewayValue(atomContext, workspace)

                /*脚本执行结束清理临时文件*/
                ScriptEnvUtils.cleanWhenEnd(buildId, workspace)
            }
        }
    }

    /**
     * 单独解析多行内容的输出
     */
    private fun parseContextFromMultiLine(
        buildId: String,
        workspace: File,
        charset: Charset = Charsets.UTF_8
    ): Map<String, String> {
        val res = mutableMapOf<String, String>()
        /*获得多行的输出内容*/
        ScriptEnvUtils.getMultipleLines(buildId, workspace, charset).forEach {
            logger.debug("multiLine:$it")
            /*解析variable或者output变量*/
            val str = CommandLineUtils.parseVariable(it) ?: CommandLineUtils.parseOutput(it)
            if (str != null) {
                val split = str.split("=", ignoreCase = false, limit = 2)
                /*解码替换回来，得到多行内容*/
                res[split[0].trim()] = escapeData(split[1].trim())
            }
        }
        return res
    }

    /**
     * 统一的异常处理模块
     */
    private fun <T> handle(
        atomResult: AtomResult,
        action: () -> T?
    ) {
        try {
            action()
        } catch (triggerE: AtomException) {
            atomResult.message = triggerE.message
            atomResult.errorCode = ErrorCode.USER_SCRIPT_COMMAND_INVAILD
            atomResult.errorType = ErrorType.USER.num
            atomResult.status = Status.failure
        } catch (e: Throwable) {
            // unknown 情况归属为插件问题，需要插件方来处理
            atomResult.message = "Unknown Error: " + e.message
            atomResult.errorCode = ErrorCode.USER_TASK_OPERATE_FAIL
            atomResult.errorType = ErrorType.PLUGIN.num
            atomResult.status = Status.error
        }
    }

    private fun escapeData(value: String): String {
        return value
            /*单引号的情况，是batch替换的结果，这里进行兼容*/
            .replace("'%0D'", "\r")
            .replace("%0D", "\r")
            .replace("'%0A'", "\n")
            .replace("%0A", "\n")
            .replace("'%25'", "%")
            .replace("%25", "%")
    }

    /**
     * 设置红线指标
     */
    private fun setGatewayValue(atomContext: AtomContext<ScriptRunAtomParam>, workspace: File) {
        /*去拿红线指标输出文件*/
        val gatewayFile = File(workspace, ScriptEnvUtils.getQualityGatewayEnvFile())
        try {
            /*不存在直接推出*/
            if (!gatewayFile.exists()) return
            val data = mutableMapOf<String, String>()
            val title = mutableMapOf<String, String>()
            // 创建红线指标
            gatewayFile.readLines().forEach {
                val split = it.split(",")
                if (split.size > 2) {
                    /*格式不对直接抛错*/
                    throw AtomException(
                        "much gateway parameter,count:${split.size}"
                    )
                }
                val nameToValue = split.getOrNull(0)
                val nameToTitle = split.getOrNull(1)
                /*去做二次切分*/
                keyEqualValueInsertMap(nameToValue, data)
                keyEqualValueInsertMap(nameToTitle, title)
            }
            /*创建红线指标*/
            updateIndicatorTitle(
                userId = atomContext.param.pipelineStartUserId,
                projectId = atomContext.param.projectName,
                data = data,
                nameMapToTitle = title
            )
            // 将自定义指标的值入库
            saveQualityData(
                taskId = atomContext.param.pipelineTaskId,
                taskName = atomContext.param.taskName,
                data = data
            )

            logger.info("save gateway value: $data")
        } catch (ignore: Exception) {
            /*处理异常*/
            logger.info("save gateway value fail: ${ignore.message}")
            logger.error("setGatewayValue|${ignore.message}", ignore)
        } finally {
            /*执行完后删除文件*/
            gatewayFile.delete()
        }
    }

    private fun keyEqualValueInsertMap(nameToValue: String?, map: MutableMap<String, String>) {
        /*为空直接退出*/
        if (nameToValue.isNullOrBlank()) return
        /*二次切分后确定最终的 key*/
        val key = nameToValue.split("=").getOrNull(0) ?: throw AtomException(
            "Illegal gateway key set: $nameToValue"
        )
        /*二次切分后确定最终的 value*/
        val value = nameToValue.split("=").getOrNull(1) ?: throw AtomException(
            "Illegal gateway key set: $nameToValue"
        )
        /*组装进map*/
        map[key] = value.trim()
    }

/*
    private fun upsertIndicator(
        userId: String,
        projectId: String,
        data: Map<String, String>
    ) {
        val indicatorCreates = data.map { (name, value) ->
            val dataType = getQualityDataType(value)
            IndicatorCreate(
                name = name,
                cnName = name,
                desc = "",
                dataType = dataType,
                operation = getQualityOperations(dataType == QualityDataType.BOOLEAN),
                threshold = value,
                elementType = QUALITY_ELEMENT_TYPE
            )
        }
        doUpsertIndicator(userId = userId, projectId = projectId, indicatorCreates = indicatorCreates)
    }*/

    private fun updateIndicatorTitle(
        userId: String,
        projectId: String,
        data: Map<String, String>,
        nameMapToTitle: Map<String, String>
    ) {
        //  dataMapExample: pass_rate to 1.0
        val indicatorCreates = data.map { (name, value) ->
            /*获取数据类型*/
            val dataType = getQualityDataType(value)
            /*获取标题*/
            val title = getTitleOrDefault(name, nameMapToTitle)
            IndicatorCreate(
                name = name,
                cnName = title,
                dataType = dataType
            )
        }
        /*调接口创建红线指标*/
        doUpsertIndicator(userId = userId, projectId = projectId, indicatorCreates = indicatorCreates)
    }

    /*获取标题的逻辑*/
    private fun getTitleOrDefault(
        name: String,
        nameMapToTitle: Map<String, String>
    ): String {
        val title = nameMapToTitle[name]
        /*变量中有则返回变量中的name*/
        if (title.isNullOrBlank()) {
            return name
        }
        return title
    }

    private fun doUpsertIndicator(
        userId: String,
        projectId: String,
        indicatorCreates: List<IndicatorCreate>
    ) {
        /*通过接口创建红线指标*/
        qualityApi.upsertIndicator(
            userId = userId, projectId = projectId, indicatorCreate = indicatorCreates
        ).data.let {
            if (it == null || !it) {
                /*返回异常情况处理*/
                throw AtomException(
                    "Failed to create run redline indicator"
                )
            }
        }
    }

    private fun getQualityDataType(value: String): QualityDataType {
        /*转int*/
        value.toIntOrNull().let {
            if (it != null) {
                return QualityDataType.INT
            }
        }
        /*转float*/
        value.toFloatOrNull().let {
            if (it != null) {
                return QualityDataType.FLOAT
            }
        }
        /*转boolean*/
        if (value == "true" || value == "false") {
            return QualityDataType.BOOLEAN
        }
        /*其他类型直接抛错*/
        throw AtomException(
            "gateWay error qualityDataType: $value,only support INT、FLOAT、BOOLEAN"
        )
    }

/*
    private fun getQualityOperations(isBoolean: Boolean) = if (isBoolean) {
        QUALITY_BOOLEAN_OPERATIONS
    } else {
        QUALITY_ALL_OPERATIONS
    }*/

    private fun saveQualityData(
        taskId: String,
        taskName: String,
        data: Map<String, String>
    ) {
        /*调接口保存红线数据*/
        qualityApi.saveScriptHisMetadata(taskId = taskId, taskName = taskName, data = data).data.let {
            if (it == null || !it) {
                /*返回异常处理*/
                throw AtomException(
                    "Failed to save run redline data"
                )
            }
        }
    }

    /**
     * 检查参数
     * @param param 请求参数
     * @param result 结果
     */
    private fun checkParam(param: ScriptRunAtomParam, result: AtomResult) {
        // 参数检查
        if (StringUtils.isBlank(param.script)) {
            result.status = Status.failure // 状态设置为失败
            result.message = "脚本内容不能为空" // 失败信息回传给插件执行框架会打印出结果
        }
    }

    private fun parseTemplate(command: String, data: Map<String, String>, dir: File): String {
        /*通用变量替换逻辑*/
        return ReplacementUtils.replace(
            command = command,
            replacement = object : ReplacementUtils.KeyReplacement {
                override fun getReplacement(key: String): String? = if (data[key] != null) {
                    data[key]
                } else {
                    null
                }
            },
            /*额外需要替换的变量*/
            contextMap = mapOf(
                WORKSPACE_CONTEXT to dir.absolutePath,
                CI_TOKEN_CONTEXT to (data[CI_TOKEN_CONTEXT] ?: ""),
                JOB_OS_CONTEXT to getOS().name
            )
        )
    }

    /*
    * 检查系统类型
    */
    private fun checkOS(shellType: ShellType, result: AtomResult) {
        val os = getOS()
        when {
            /*非windows不能使用cmd（batch）*/
            os != OSType.WINDOWS && shellType == ShellType.CMD ||
                /*非windows不能使用POWERSHELL_DESKTOP*/
                os != OSType.WINDOWS && shellType == ShellType.POWERSHELL_DESKTOP ||
                /*windows不能使用sh*/
                os == OSType.WINDOWS && shellType == ShellType.SH -> {
                result.status = Status.failure
                result.message = "$os script execution failed"
                /*不满足的情况直接用户抛错*/
                throw AtomException(
                    "The current system(${os.name}) does not support: ${shellType.shellName}"
                )
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScriptRunAtom::class.java)
    }
}
