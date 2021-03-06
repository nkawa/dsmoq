package dsmoq.models;

typedef Dataset = {
    var id(default, null): String;
    var files(default, null): Array<DatasetFile>;
    var meta(default, null): DatasetMetadata;
    var images(default, null): Array<Image>;
    var primaryImage(default, null): Image;
    var featuredImage(default, null): Image;
    var ownerships(default, null): Array<DatasetOwnership>;
    var defaultAccessLevel(default, null): DatasetGuestAccessLevel;
    var permission(default, null): DatasetPermission;
    var accessCount(default, null): Int;
    var localState(default, null): Int;
    var s3State(default, null): Int;
    var filesCount(default, null): Int;
    var fileLimit(default, null): Int;
	var app(default, null): DatasetApp;
	var appUrl(default, null): String;
}
