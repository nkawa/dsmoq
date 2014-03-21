package pages;

typedef User = {userId: String, userName: String}
typedef GroupId = String

enum LoginStatus {
    Anonymouse;
    LogedIn(user: User);
}

typedef JsonResponse<A> = {status: String, ?data: A, ?message: String}

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

enum DefaultLevel {
    Deny;
    LimitedPublic;
    FullPublic;
}

typedef AclGroup = {id: String, name: String}
