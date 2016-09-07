$(function() {
	$("button.publish").click(function() {
		var userId = $("input.user_id").val();
		if (userId == "") {
			$("p.error").text("ユーザーIDが指定されていません。");
			return false;
		}
	});
});
