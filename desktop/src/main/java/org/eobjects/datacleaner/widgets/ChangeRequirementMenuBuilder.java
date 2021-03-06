/**
 * DataCleaner (community edition)
 * Copyright (C) 2014 Neopost - Customer Information Management
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.eobjects.datacleaner.widgets;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.swing.Icon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import org.eobjects.analyzer.descriptors.FilterBeanDescriptor;
import org.eobjects.analyzer.job.AnyComponentRequirement;
import org.eobjects.analyzer.job.ComponentRequirement;
import org.eobjects.analyzer.job.FilterOutcome;
import org.eobjects.analyzer.job.SimpleComponentRequirement;
import org.eobjects.analyzer.job.builder.AbstractBeanJobBuilder;
import org.eobjects.analyzer.job.builder.AbstractBeanWithInputColumnsBuilder;
import org.eobjects.analyzer.job.builder.FilterJobBuilder;
import org.eobjects.analyzer.util.LabelUtils;
import org.eobjects.datacleaner.util.IconUtils;
import org.eobjects.datacleaner.util.ImageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Object capable of building a menu for changing a component's
 * {@link ComponentRequirement}.
 */
public class ChangeRequirementMenuBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ChangeRequirementMenuBuilder.class);

    public static final String NO_REQUIREMENT_TEXT = "(No requirement)";
    public static final String ANY_REQUIREMENT_TEXT = "All records";

    private static final ImageManager imageManager = ImageManager.get();

    private static final Icon selectedRequirementIcon = imageManager.getImageIcon(IconUtils.STATUS_VALID,
            IconUtils.ICON_SIZE_SMALL);
    private static final Icon unconfiguredFilterIcon = imageManager.getImageIcon(IconUtils.STATUS_WARNING,
            IconUtils.ICON_SIZE_SMALL);

    private final AbstractBeanJobBuilder<?, ?, ?> _componentBuilder;

    public ChangeRequirementMenuBuilder(AbstractBeanJobBuilder<?, ?, ?> componentBuilder) {
        _componentBuilder = componentBuilder;
    }

    public List<JMenuItem> createMenuItems() {
        final ComponentRequirement currentComponentRequirement = _componentBuilder.getComponentRequirement();
        logger.info("Current requirement: {}", currentComponentRequirement);

        final Collection<FilterOutcome> currentFilterOutcomes = currentComponentRequirement == null ? Collections
                .<FilterOutcome> emptyList() : currentComponentRequirement.getProcessingDependencies();

        final List<JMenuItem> popup = new ArrayList<>();
        final JMenuItem noFilterMenuItem = new JMenuItem(NO_REQUIREMENT_TEXT);
        noFilterMenuItem
                .setToolTipText("Do not apply any specific requirements on this component, except for those that are transitively inherited by the configuration.");
        noFilterMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                _componentBuilder.setComponentRequirement(null);
                onRequirementChanged();
            }
        });
        popup.add(noFilterMenuItem);

        final JMenuItem anyFilterMenuItem = new JMenuItem(ANY_REQUIREMENT_TEXT);
        anyFilterMenuItem
                .setToolTipText("Explicitly accept all records into this component, regardless of any other transitive requirements.");
        anyFilterMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                _componentBuilder.setComponentRequirement(AnyComponentRequirement.get());
                onRequirementChanged();
            }
        });
        if (AnyComponentRequirement.get().equals(_componentBuilder.getComponentRequirement())) {
            anyFilterMenuItem.setIcon(selectedRequirementIcon);
        }
        popup.add(anyFilterMenuItem);

        // if this JobBuilder is a FilterJobBuilder, remove it from the list of
        // available filters
        final List<FilterJobBuilder<?, ?>> fjbs = getFilterJobBuilders();

        for (final FilterJobBuilder<?, ?> fjb : fjbs) {
            final JMenu filterMenuItem = new JMenu(LabelUtils.getLabel(fjb));

            if (!fjb.isConfigured()) {
                filterMenuItem.setIcon(unconfiguredFilterIcon);
                filterMenuItem.setEnabled(false);
                filterMenuItem.setToolTipText("Filter is not correctly configured");
            } else if (!validateRequirementSource(fjb)) {
                filterMenuItem.setEnabled(false);
                filterMenuItem.setToolTipText("Requirement not possible");
            } else {

                final FilterBeanDescriptor<?, ?> fjbDescriptor = fjb.getDescriptor();
                final Set<String> categoryNames = fjbDescriptor.getOutcomeCategoryNames();
                for (final String category : categoryNames) {
                    final JMenuItem categoryMenuItem = new JMenuItem(category);
                    try {
                        final Enum<?> outcomeCategory = fjbDescriptor.getOutcomeCategoryByName(category);
                        final FilterOutcome filterOutcome = fjb.getFilterOutcome(outcomeCategory);
                        if (currentFilterOutcomes.contains(filterOutcome)) {
                            filterMenuItem.setIcon(selectedRequirementIcon);
                            categoryMenuItem.setIcon(selectedRequirementIcon);
                        }
                    } catch (Exception ex) {
                        logger.info("Filterjob matching threw exception, probably because of incomplete configuration",
                                ex);
                    }

                    categoryMenuItem.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            final Enum<?> outcome = fjb.getDescriptor().getOutcomeCategoryByName(category);
                            final FilterOutcome filterOutcome = fjb.getFilterOutcome(outcome);
                            final ComponentRequirement newRequirement = new SimpleComponentRequirement(filterOutcome);
                            _componentBuilder.setComponentRequirement(newRequirement);
                            onRequirementChanged();
                        }
                    });

                    filterMenuItem.add(categoryMenuItem);
                }

            }
            popup.add(filterMenuItem);
        }

        return popup;
    }

    private List<FilterJobBuilder<?, ?>> getFilterJobBuilders() {
        final List<FilterJobBuilder<?, ?>> fjbs;
        if (_componentBuilder instanceof FilterJobBuilder<?, ?>) {
            fjbs = new LinkedList<FilterJobBuilder<?, ?>>(_componentBuilder.getAnalysisJobBuilder()
                    .getFilterJobBuilders());
            fjbs.remove(_componentBuilder);
        } else {
            fjbs = _componentBuilder.getAnalysisJobBuilder().getFilterJobBuilders();
        }
        return fjbs;
    }

    private boolean validateRequirementSource(FilterJobBuilder<?, ?> fjb) {
        if (_componentBuilder instanceof AbstractBeanWithInputColumnsBuilder) {
            AbstractBeanWithInputColumnsBuilder<?, ?, ?> abstractBeanWithInputColumnsBuilder = (AbstractBeanWithInputColumnsBuilder<?, ?, ?>) _componentBuilder;
            return abstractBeanWithInputColumnsBuilder.validateRequirementSource(fjb);
        }
        return true;
    }

    protected void onRequirementChanged() {
    }

    public boolean isFilterRequirementsAvailable() {
        return !getFilterJobBuilders().isEmpty();
    }
}
