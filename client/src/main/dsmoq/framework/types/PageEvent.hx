package dsmoq.framework.types;

/**
 * @author terurou
 */
enum PageEvent<TPage: EnumValue> {
    Navigate(page: TPage);
    NavigateAsBackword(page: TPage);
    Foward;
    Backward;
    //TODO APIレスポンスでログアウト等を検知したときに処理が必要
}