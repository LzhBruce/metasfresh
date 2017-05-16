package de.metas.material.dispo;

import java.util.List;

import org.springframework.stereotype.Service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import de.metas.material.dispo.Candidate.SubType;
import de.metas.material.dispo.Candidate.Type;
import de.metas.material.event.EventDescr;
import de.metas.material.event.MaterialEventService;
import de.metas.material.event.PPOrderRequestedEvent;
import de.metas.material.event.pporder.PPOrder;
import de.metas.material.event.pporder.PPOrder.PPOrderBuilder;
import de.metas.material.event.pporder.PPOrderLine;
import lombok.NonNull;

/*
 * #%L
 * metasfresh-manufacturing-dispo
 * %%
 * Copyright (C) 2017 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
@Service
public class CandidateService
{
	private final CandidateRepository candidateRepository;
	private final MaterialEventService materialEventService;

	public CandidateService(
			@NonNull final CandidateRepository candidateRepository,
			@NonNull final MaterialEventService materialEventService)
	{
		this.materialEventService = materialEventService;
		this.candidateRepository = candidateRepository;
	}

	public void requestPPOrder(@NonNull final Integer groupId)
	{
		final List<Candidate> group = candidateRepository.retrieveGroup(groupId);
		if (group.isEmpty())
		{
			return;
		}

		switch (group.get(0).getSubType())
		{
			case PRODUCTION:
				requestProductionOrder(group);
				break;

			default:
				break;
		}

	}

	/**
	 *
	 * @param group a non-empty list of candidates that all have {@link SubType#PRODUCTION},
	 *            all have the same {@link Candidate#getGroupId()}
	 *            and all have appropriate not-null {@link Candidate#getProductionDetail()}s.
	 * @return
	 */

	private void requestProductionOrder(@NonNull final List<Candidate> group)
	{
		final PPOrderRequestedEvent ppOrderRequestEvent = createRequestEvent(group);
		materialEventService.fireEvent(ppOrderRequestEvent);
	}

	@VisibleForTesting
	PPOrderRequestedEvent createRequestEvent(final List<Candidate> group)
	{
		Preconditions.checkArgument(!group.isEmpty(), "Param 'group' is an empty list");

		PPOrderBuilder ppOrderBuilder = PPOrder.builder();

		for (final Candidate groupMember : group)
		{
			if (groupMember.getDemandDetail() != null && groupMember.getDemandDetail().getOrderLineId() > 0)
			{
				ppOrderBuilder = ppOrderBuilder.orderLineId(groupMember.getDemandDetail().getOrderLineId());
			}

			final ProductionCandidateDetail prodDetail = groupMember.getProductionDetail();
			if (prodDetail.getPlantId() > 0)
			{
				// we talk about a ppOrder (header)
				ppOrderBuilder = ppOrderBuilder
						.productPlanningId(prodDetail.getProductPlanningId())
						.datePromised(groupMember.getDate())
						.orgId(groupMember.getOrgId())
						.plantId(prodDetail.getPlantId())
						.productId(groupMember.getProductId())
						.quantity(groupMember.getQuantity())
						.uomId(prodDetail.getUomId())
						.warehouseId(groupMember.getWarehouseId());
			}
			else
			{
				final boolean receipt = groupMember.getType() == Type.SUPPLY;
				if (receipt)
				{
					ppOrderBuilder.datePromised(groupMember.getDate());
				}
				else
				{
					ppOrderBuilder.dateStartSchedule(groupMember.getDate());
				}

				ppOrderBuilder.line(
						PPOrderLine.builder()
								.description(prodDetail.getDescription())
								.productBomLineId(prodDetail.getProductBomLineId())
								.productId(groupMember.getProductId())
								.qtyRequired(groupMember.getQuantity())
								.productBomLineId(prodDetail.getProductBomLineId())
								.receipt(receipt)
								.build());
			}
		}
		return PPOrderRequestedEvent.builder()
				.eventDescr(new EventDescr())
				.eventDescr(new EventDescr())
				.ppOrder(ppOrderBuilder.build())
				.reference(group.get(0).getReference())
				.build();
	}
}