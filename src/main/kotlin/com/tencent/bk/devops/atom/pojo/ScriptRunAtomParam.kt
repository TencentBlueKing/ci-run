package com.tencent.bk.devops.atom.pojo

import com.fasterxml.jackson.annotation.JsonProperty
import lombok.Data
import lombok.EqualsAndHashCode

/**
 * 插件参数定义
 * @version 1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
class ScriptRunAtomParam : AtomBaseParam() {

    /*脚本内容*/
    val script: String = ""
    /*字符集类型*/
    @JsonProperty("charsetType")
    val charSetType: String = ""

    val manualCommand: String = ""
    /*脚本类型*/
    val shell: String = ""
}
