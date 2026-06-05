package com.vyttah.goaml.engine.build;

import com.vyttah.goaml.domain.generated.ActivityType;
import com.vyttah.goaml.domain.generated.Report;
import com.vyttah.goaml.domain.generated.ReportPartyType;
import com.vyttah.goaml.domain.generated.TTransItem;

import java.util.List;
import java.util.function.Function;

/**
 * Helpers that hide the goAML JAXB <em>wrapper classes</em> — the {@code <xs:complexType>}s the schema wraps
 * around repeating elements (e.g. {@code <phones><phone/></phones>}). xjc generates a distinct wrapper type per
 * owner (e.g. {@code TEntity.Phones} ≠ {@code TPersonMyClient.Phones}), so a single {@code phones(...)} helper
 * is impossible; the generic {@link #wrap} covers every leaf wrapper, and the activity-level wrappers the
 * {@link DpmsrReportBuilder} needs have dedicated convenience methods.
 */
public final class GoamlWrappers {

    private GoamlWrappers() {}

    /** Wrap a list of report parties into the activity's {@code <report_parties>} container. */
    public static ActivityType.ReportParties reportParties(List<ReportPartyType> parties) {
        ActivityType.ReportParties wrapper = new ActivityType.ReportParties();
        if (parties != null) {
            wrapper.getReportParty().addAll(parties);
        }
        return wrapper;
    }

    /** Wrap a list of goods/services items into the activity's {@code <goods_services>} container. */
    public static ActivityType.GoodsServices goodsServices(List<TTransItem> items) {
        ActivityType.GoodsServices wrapper = new ActivityType.GoodsServices();
        if (items != null) {
            wrapper.getItem().addAll(items);
        }
        return wrapper;
    }

    /** Wrap report-indicator codes into the report's {@code <report_indicators>} container. */
    public static Report.ReportIndicators reportIndicators(List<String> indicators) {
        Report.ReportIndicators wrapper = new Report.ReportIndicators();
        if (indicators != null) {
            wrapper.getIndicator().addAll(indicators);
        }
        return wrapper;
    }

    /**
     * Generic leaf-wrapper helper: build any wrapper {@code W}, append {@code items} to the list it exposes,
     * and return it — e.g. {@code wrap(new TEntity.Phones(), TEntity.Phones::getPhone, phone1, phone2)}.
     *
     * @param wrapper a freshly-constructed wrapper instance
     * @param list    the wrapper's repeating-element list accessor (a method reference)
     * @param items   the elements to add
     */
    @SafeVarargs
    public static <W, E> W wrap(W wrapper, Function<W, List<E>> list, E... items) {
        List<E> target = list.apply(wrapper);
        for (E item : items) {
            target.add(item);
        }
        return wrapper;
    }
}
