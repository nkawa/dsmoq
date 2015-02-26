package dsmoq;
import dsmoq.CKEditor.Editor;

@:native("CKEDITOR")
extern class CKEditor {
	public static function replace(element: String): Editor;
}

@:native("CKEDITOR.editor")
extern class Editor {
	public function getData(internal: Bool): String;
	public function setData(data: String): Void;
	public function on(eventName: String, listener: EventInfo -> Void): Dynamic;
	public function fire(eventName: String, ?data: Dynamic, ?editor: Editor): Dynamic;
	public function fireOnce(eventName: String, ?data: Dynamic, ?editor: Editor): Dynamic;
	public function destroy(): Void;
}

@:native("CKEDITOR.eventInfo")
extern class EventInfo {
	public var data: Dynamic;
	public var editor: Editor;
	public var name : String;
}
