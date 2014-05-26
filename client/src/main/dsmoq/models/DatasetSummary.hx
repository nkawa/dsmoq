package dsmoq.models;

typedef DatasetSummary = {
    var id(default, never): String;
    var name(default, never): String;
    var description(default, never): String;
    var image(default, never): String;
    var license(default, never): String;
    var attributes(default, never): Array<License>;
    var ownerships(default, never): Array<DatasetOwnership>;
    var files(default, never): UInt;
    var dataSize(default, never): UInt;
    var defaultAccessLevel(default, never): GuestAccessLevel;
    var permission(default, never): DatasetPermission;
}