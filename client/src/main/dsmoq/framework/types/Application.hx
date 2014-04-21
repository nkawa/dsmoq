package dsmoq.framework.types;

/**
 * @author terurou
 */
typedef Application<TPage: EnumValue> = {
    initialize: Void -> Replacable<Html, Void, PageEvent<TPage>>,
    toUrl: TPage -> PageInfo,
    fromUrl: PageInfo -> TPage,
    render: TPage -> Option<Html> -> Rendered<Void, PageEvent<TPage>>
}