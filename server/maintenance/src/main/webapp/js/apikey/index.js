$(function() {
    $("button.disabling").click(function() {
        if (!confirm("APIキー無効化を実行してもよろしいでしょうか？")) {
            return false;
        }
    });
});
