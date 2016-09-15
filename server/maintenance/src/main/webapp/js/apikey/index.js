$(function() {
	$("button.disabling").click(function() {
//		var checked = $("input.consumerKey").filter(function(e) {
//			return $(this).prop("checked");
//		});
//		if (checked.length == 0) {
//			$("p.error").text("キーが未選択です。");
//			return false;
//		}
		if (!confirm("APIキー無効化を実行してもよろしいでしょうか？")) {
			return false;
		}
	});
});
