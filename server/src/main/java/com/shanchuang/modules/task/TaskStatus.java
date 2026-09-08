package com.shanchuang.modules.task;

/** 任务与子项状态。用字符串常量而不是枚举，方便和数据库里的值一一对应 */
public final class TaskStatus {

    public static final String PENDING = "PENDING";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    /** 部分成功：批量里有的成有的败 */
    public static final String PARTIAL = "PARTIAL";
    public static final String FAILED = "FAILED";
    public static final String CANCELLED = "CANCELLED";
    /** 拆解专用：取件失败等用户补素材，不算失败 */
    public static final String NEED_FILE = "NEED_FILE";

    public static final String TYPE_GENERATE = "SCRIPT_GENERATE";
    public static final String TYPE_REWRITE = "SCRIPT_REWRITE";
    public static final String TYPE_EXTRACT = "VIDEO_EXTRACT";

    private TaskStatus() {
    }

    public static boolean isTerminal(String status) {
        return SUCCESS.equals(status) || PARTIAL.equals(status)
                || FAILED.equals(status) || CANCELLED.equals(status);
    }
}
