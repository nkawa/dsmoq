package dsmoq.pages;

import js.pnotify.PNotify;

class Notification {
    public static function show(type: String, message: String): Void {
        new PNotify({
            type: type,
            delay: 4000,
            buttons: { sticker: false },
            text: message
        });
    }
}