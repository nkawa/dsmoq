package dsmoq;

import hxgnd.PositiveInt;

enum Page {
	Top;
    Dashboard;

    //DatasetNew;
    DatasetList(page: Int, query: String, filters: Array<Dynamic>);
    DatasetShow(id: String);
    DatasetEdit(id: String);

    //GroupNew;
    GroupList(page: Int, query: String);
    GroupShow(id: String);
    GroupEdit(id: String);

    Profile;
}