package com.tencent.bk.devops.atom.common

object ErrorCode {
    // 插件执行错误
    const val PLUGIN_DEFAULT_ERROR = 800001 // 插件异常默认
    const val PLUGIN_CREATE_QUALITY_INDICATOR_ERROR = 800002 // 创建质量红线指标异常
    const val PLUGIN_SAVE_QUALITY_DATA_ERROR = 800003 // 报错质量红线数据异常

    // 用户使用错误
    const val USER_INPUT_INVAILD = 800004 // 用户输入数据有误
    const val USER_TASK_OPERATE_FAIL = 800005 // 插件执行过程出错
    const val USER_SCRIPT_COMMAND_INVAILD = 800006 // 脚本命令无法正常执行
    const val USER_ERROR_MESSAGE = "800007"
}
