package pages;

typedef DatasetSummary ={
    id: String,
    name: String,
    description: String,
    image: String,
    license: String,
    attributes: String,
    owners: String,
    groups: String,
    files: String,
    dataSize: String,
    defaultAccessLevel: String,
    permission: String
}

enum AclLevel {
    LimitedPublic;
    FullPublic;
    Owner; // this is for User, it's Provider if for Group
}

typedef AclGroup = {id: String, name: String}
