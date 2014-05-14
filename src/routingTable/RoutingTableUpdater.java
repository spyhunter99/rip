package routingTable;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;

import fileParser.OutputPortInformation;

public class RoutingTableUpdater {
	/**
	 * Sets routing table metrics to infinity when these metrics were learned
	 * from the neighbour that this routing table is being sent to. This
	 * implements split horizon with poisoned reverse
	 * 
	 * @param input
	 *            The current routing table for this router
	 * @param neighbor
	 *            RouterId of the neighbouring router which the output table
	 *            will be sent to
	 */
	public void SetMetricsToInfinity(RoutingTable input, int neighbor) {
		for (RoutingTableRow row : input.getRows()) {
			RoutingTableRow currentRow = row;
			if (currentRow.LearnedFrom == neighbor) {
				currentRow.LinkCost = 16;
			}
		}
	}

	/**
	 * 
	 * @param input
	 *            Input routing table
	 * @param linkCost
	 *            Cost to be added to each row
	 */
	public void AddLinkCost(RoutingTable input, int linkCost) {
		for (RoutingTableRow row : input.getRows()) {
			row.LinkCost += linkCost;
		}
	}

	public void RemoveRowsFlaggedForDeletion(RoutingTable input) {
		Iterator<RoutingTableRow> rowIterator = input.getRows().iterator();

		while (rowIterator.hasNext()) {
			RoutingTableRow row = rowIterator.next();
			if (row.DeleteThisRow) {
				System.out.println("ROW REMOVED: " + row.DestRouterId);
				rowIterator.remove();
			}
		}

	}

	public void ProcessIncomingRoutingTable(RoutingTable current,
			RoutingTable received, int myRouterId,
			ArrayList<OutputPortInformation> myOutputPorts) {

		for (RoutingTableRow receivedRow : received.getRows()) {
			boolean matched = false;
				for (RoutingTableRow currentRow : current.getRows()) {

					if (receivedRow.DestRouterId == currentRow.DestRouterId) {
						matched = true;

						if (receivedRow.LinkCost < currentRow.LinkCost && currentRow.LinkCost != 16) {
							// Replace current row with received row
							current.Rows.remove(currentRow);

							current.Rows.add(receivedRow);
							receivedRow.NextHopRouterId = received.MyRouterId;
							receivedRow.LearnedFrom = received.MyRouterId;
							receivedRow.NextHopPortNumber = GetOutputPortFromRouterId(
									myOutputPorts, received.MyRouterId);
							receivedRow.InitializeAndResetRowTimeoutTimer();
						} else if (receivedRow.LinkCost < 16){
							//We have received a valid entry for the current row
							//It is not cheaper so don't replace the current row
							//Just reinitialise the timeout timer.
							currentRow.InitializeAndResetRowTimeoutTimer();
						}
					}
					//A neighbouring router has come back online, reset the link cost
					//of its row if it is still in the table
					if (received.MyRouterId == currentRow.DestRouterId
							&& currentRow.LinkCost == 16) {
						currentRow.InitializeAndResetRowTimeoutTimer();
						currentRow.LinkCost = GetLinkCostFromNextHopRouterId(
								myOutputPorts, currentRow.NextHopRouterId);
					}

				}
				 //We have received a new valid row, add it to our table
			/*if (!matched && receivedRow.DestRouterId != myRouterId
					&& receivedRow.LinkCost < 16) {*/
			if(!matched && receivedRow.LinkCost < 16){
				current.Rows.add(receivedRow);
				receivedRow.NextHopRouterId = received.MyRouterId;
				receivedRow.LearnedFrom = received.MyRouterId;
				receivedRow.NextHopPortNumber = GetOutputPortFromRouterId(
						myOutputPorts, received.MyRouterId);
				receivedRow.InitializeAndResetRowTimeoutTimer();

			}
		}
	}

	private int GetOutputPortFromRouterId(
			ArrayList<OutputPortInformation> outputPorts, int routerId) {
		for (OutputPortInformation output : outputPorts) {
			if (output.RouterId == routerId) {
				return output.PortNumber;
			}
		}
		return 0;
	}

	private int GetLinkCostFromNextHopRouterId(
			ArrayList<OutputPortInformation> outputPorts, int nextHopRouterId) {
		for (OutputPortInformation output : outputPorts) {
			if (output.RouterId == nextHopRouterId) {
				return output.LinkCost;
			}
		}
		return 0;
	}

	public void MarkRowsAsInvalid(RoutingTable current, int routerId) {
		for (RoutingTableRow row : current.getRows()) {
			if (row.NextHopRouterId == routerId) {
				row.LinkCost = 16;
				row.InitializeAndResetDeletionTimer();
			}
		}
	}
}
