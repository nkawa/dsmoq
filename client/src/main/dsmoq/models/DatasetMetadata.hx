package dsmoq.models;

typedef DatasetMetadata = {
    var name(default, null): String;
    var description(default, null): String;
    var license(default, null): String;
    var attributes(default, null): Array<DatasetAttribute>;
}