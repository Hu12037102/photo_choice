# ============================================================
# PhotoChoice SDK —— 随库下发给宿主 App 的 R8/ProGuard keep 规则
# 宿主 App 开启 minify(release 包)时自动合并生效，保证：
#   1) 公开入口 API 不被裁剪/改名；
#   2) 经 Intent 以 java.io.Serializable 跨 Activity 传递的配置对象
#      反序列化不因字段改名/裁剪而失败。
# ============================================================

# ── 公开入口 API ────────────────────────────────────────────
# Builder 链式入口 / Contract / 结果模型：宿主直接调用，保留公开成员
-keep public class com.google.photochoice.PhotoChoice { public *; }
-keep public class com.google.photochoice.PhotoChoice$Builder { public *; }
-keep public class com.google.photochoice.PhotoChoiceContract { public *; }
-keep public class com.google.photochoice.PhotoChoiceResult { *; }

# ── 配置对象(Serializable，经 Intent 传递) ──────────────────
# R8 改名/裁字段会导致 getSerializableExtra 还原失败或字段丢失，全量保留
-keep class com.google.photochoice.config.** { *; }
-keepclassmembers class com.google.photochoice.config.** {
    <fields>;
    <init>(...);
}

# ── Serializable 契约必需成员 ───────────────────────────────
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ── 枚举 valueOf/values 反射保留 ────────────────────────────
-keepclassmembers enum com.google.photochoice.config.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
