$(function() {
	$("button.search").click(function() {
		window.location.href = "users.html";
	});
	$("button.update").click(function() {
		$("div.results").empty();
		if (confirm("更新を実行してもよろしいでしょうか？")) {
			$("input.disable").prop("checked", false);
		}
	});
});
