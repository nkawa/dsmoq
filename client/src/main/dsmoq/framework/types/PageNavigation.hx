package dsmoq.framework.types;

/**
 * @author terurou
 */
enum PageNavigation<TPage: EnumValue> {
    Navigate(page: TPage);
    Reload;
    Foward;
    Back;
}