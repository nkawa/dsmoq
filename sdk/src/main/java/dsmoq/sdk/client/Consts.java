package dsmoq.sdk.client;

/**
 * 定数クラス
 */
public class Consts {

    /**
     * オーナータイプ
     * (Datasetにアクセス権を設定する場合に使用します。)
     */
    public static class OwnerType {
        public static final int User = 1;
        public static final int Group = 2;
    }

    /**
     * アクセスレベル
     * (Datasetにアクセス権を設定する場合に使用します。)
     */
    public static class AccessLevel {
        public static final int Deny = 0;
        public static final int LimitedPublic = 1;
        public static final int FullPublic = 2;
        public static final int Owner = 3;
    }

    /**
     * ゲストアクセスレベル
     * (Datasetにゲストユーザーのアクセス権を設定する場合に使用します。)
     */
    public static class GuestAccessLevel {
        public static final int Deny = 0;
        public static final int LimitedPublic = 1;
        public static final int FullPublic = 2;
    }

    /**
     * ロール
     * （グループにメンバーを追加する場合に使用します。）
     */
    public static class Role {
        public static final int Member = 1;
        public static final int Manager = 2;
    }

}
