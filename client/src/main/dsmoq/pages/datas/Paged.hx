package dsmoq.pages.datas;

/**
 * ページネーション用のデータ構造です。
 */
typedef Paged<T> = {
    /** 現在のページ番号 (0起算) */
    var index: Int;
    /** 1ページあたりの要素数 */
    var limit: Int;
    /** 総数 */
    var total: Int;
    /** ページ内の要素 */
    var items: Array<T>;
    /** 総ページ数 */
    var pages: Int;
    /** 読み込み中か */
    @:optional var useProgress: Bool;
};
