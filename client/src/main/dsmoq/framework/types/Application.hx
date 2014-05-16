package dsmoq.framework.types;

/**
 * @author terurou
 */
typedef Application<TPage: EnumValue> = {
    function frame(): PageFrame<TPage>;
    function content(page: TPage): PageContent<TPage>;
    function toLocation(page: TPage): Location;
    function fromLocation(location: Location): Option<TPage>;
}