package dsmoq.framework.types;

/**
 * ...
 * @author terurou
 */
typedef PageContainer<TPage: EnumValue> = Replacable<PageComponent<TPage>, Void, PageEvent<TPage>>;