package dsmoq.models;

typedef DatasetFile = {
    var id: String;
    var name: String;
    var description: String;
    var url: String;
    var size: UInt;
    var createdAt: String; //TODO Date
    var createdBy: Null<Profile>;
    var updatedAt: String; //TODO Date
    var updatedBy: Null<Profile>;
    var isZip: Bool;
    var zipedFiles: Array<DatasetZipedFile>;
    var zipCount: Int;
}
