$(function() {
    $("#localUserForm").submit(function(event) {
        var userId = $("input#userName");
        if (userId.validity.valueMissing) {
            userId.setCustomValidity("必須入力項目です");
            return false;
        } else if (userId.validity.patternMismatch) {
            userId.setCustomValidity("半角英数字で入力してください");
            return false;
        } else {
           userId.setCustomValidity("");
        }
        var password = $("input#password");
        if (password.validity.valueMissing) {
            password.setCustomValidity("必須入力項目です");
            return false;
        } else {
           password.setCustomValidity("");
        }

        if (!confirm("ユーザーを作成します。よろしいでしょうか？")) {
            return false;
        }
    });
});
