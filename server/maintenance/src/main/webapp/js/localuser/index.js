$(function() {
    $("button.create_user").click(function() {
        if (!confirm("ユーザーを作成します。よろしいでしょうか？")) {
            return false;
        }
    });
});
