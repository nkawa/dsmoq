$(function() {
    $("button.update").click(function() {
        if (!confirm("メンバーを更新してもよろしいでしょうか？")) {
            return false;
        }
    });
    $("button.delete").click(function() {
        if (!confirm("メンバーを削除してもよろしいでしょうか？")) {
            return false;
        }
    });
});
