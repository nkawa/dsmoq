package dsmoq;

@:native("CSV")
extern class CSV {
	public function new(data: Dynamic, ?options:Dynamic);
	public function encode(): String;
	public function parse(): Array<Dynamic>;
	public function forEach(callback: Dynamic -> Void): Void;
}