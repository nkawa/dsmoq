package dsmoq;

import hxgnd.PositiveInt;

enum Page {
    Dashboard;

    //DatasetNew;
    DatasetList(page: PositiveInt, query: String, filters: Dynamic);
    DatasetShow(id: String);
    DatasetEdit(id: String);

    //GroupNew;
    GroupList(page: PositiveInt, query: String);
    GroupShow(id: String);
    GroupEdit(id: String);

    Profile;
}