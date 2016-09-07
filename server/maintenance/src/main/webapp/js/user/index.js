$(function() {
	$("button.update").click(function() {
		if (!confirm("更新を実行してもよろしいでしょうか？")) {
			return false;
		}
	});
});
