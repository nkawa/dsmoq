$(function() {
    $("button.update").click(function() {
        if (!confirm("アクセス権を更新してもよろしいでしょうか？")) {
            return false;
        }
    });
    $("button.delete").click(function() {
        if (!confirm("アクセス権を削除してもよろしいでしょうか？")) {
            return false;
        }
    });
});
