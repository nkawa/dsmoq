package dsmoq.models;

typedef Dataset = {
    var id(default, null): String;
    var files(default, null): Array<DatasetFile>;
    var meta(default, null): DatasetMetadata;
    var images(default, null): Array<Image>;
    var primaryImage(default, null): Image;
    var ownerships(default, null): Array<DatasetOwnership>;
    var defaultAccessLevel(default, null): DatasetAccessLevel;
    var permission(default, null): DatasetPermission;
}