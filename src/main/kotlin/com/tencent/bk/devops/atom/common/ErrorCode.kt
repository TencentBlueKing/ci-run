package com.tencent.bk.devops.atom.common

object ErrorCode {
    // 插件执行错误
    const val PLUGIN_DEFAULT_ERROR = 2199001 // 插件异常默认
    const val PLUGIN_CREATE_QUALITY_INDICATOR_ERROR = 2199002
    const val PLUGIN_SAVE_QUALITY_DATA_ERROR = 2199003
    // 用户使用错误
    const val USER_INPUT_INVAILD = 2199002 // 用户输入数据有误
    const val USER_RESOURCE_NOT_FOUND = 2199003 // 找不到对应系统资源
    const val USER_TASK_OPERATE_FAIL = 2199004 // 插件执行过程出错
    const val USER_JOB_OUTTIME_LIMIT = 2199005 // 用户Job排队超时（自行限制）
    const val USER_TASK_OUTTIME_LIMIT = 2199006 // 用户插件执行超时（自行限制）
    const val USER_QUALITY_CHECK_FAIL = 2199007 // 质量红线检查失败
    const val USER_QUALITY_REVIEW_ABORT = 2199008 // 质量红线审核驳回
    const val USER_SCRIPT_COMMAND_INVAILD = 2199009 // 脚本命令无法正常执行
    const val USER_STAGE_FASTKILL_TERMINATE = 2199010 // 因用户配置了FastKill导致的终止执行
    const val USER_SCRIPT_TASK_FAIL = 2199011 // bash脚本发生用户错误
}
