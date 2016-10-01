$(function() {
    $("button.logical_delete").click(function() {
        if (!confirm("論理削除を実行してもよろしいでしょうか？")) {
            return false;
        }
    });
    $("button.cancel_logical_delete").click(function() {
        if (!confirm("論理削除解除を実行してもよろしいでしょうか？")) {
            return false;
        }
    });
    $("button.physical_delete").click(function() {
        if (!confirm("物理削除を実行してもよろしいでしょうか？")) {
            return false;
        }
    });
});
