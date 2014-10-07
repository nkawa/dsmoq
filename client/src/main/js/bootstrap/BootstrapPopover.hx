package js.bootstrap;
import hxgnd.js.Html;

class BootstrapPopover {
    public static function initialize(target: Html, ?options: BootstrapPopoverOption): Void {
        untyped target.popover(options);
    }

    public static function show(target: Html): Void {
        untyped target.popover("show");
    }

    public static function hide(target: Html): Void {
        untyped target.popover("hide");
    }

    public static function toggle(target: Html): Void {
        untyped target.popover("toggle");
    }

    public static function destroy(target: Html): Void {
        untyped target.popover("destroy");
    }
}

typedef BootstrapPopoverOption = {
    ?animation: Bool,
    ?container: Dynamic,
    ?content: Dynamic,
    ?delay: Dynamic,
    ?html: Bool,
    ?placement: Dynamic,
    ?selector: Dynamic,
    ?template: String,
    ?title: Dynamic,
    ?trigger: String,
    ?viewport: Dynamic
}