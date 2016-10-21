package org.briarproject.android.threaded;

import android.animation.ValueAnimator;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import org.briarproject.android.util.VersionedAdapter;
import org.briarproject.api.sync.MessageId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.support.v7.widget.RecyclerView.NO_POSITION;

public abstract class ThreadItemAdapter<I extends ThreadItem>
		extends RecyclerView.Adapter<ThreadItemViewHolder<I>>
		implements VersionedAdapter {

	static final int UNDEFINED = -1;

	private final NestedTreeList<I> items = new NestedTreeList<>();
	private final Map<I, ValueAnimator> animatingItems = new HashMap<>();
	private final ThreadItemListener<I> listener;
	private final LinearLayoutManager layoutManager;

	// highlight not dependant on time
	private I replyItem;
	// temporary highlight
	private I addedItem;

	private volatile int revision = 0;

	public ThreadItemAdapter(ThreadItemListener<I> listener,
			LinearLayoutManager layoutManager) {
		this.listener = listener;
		this.layoutManager = layoutManager;
	}

	@Override
	public void onBindViewHolder(ThreadItemViewHolder<I> ui, int position) {
		I item = getVisibleItem(position);
		if (item == null) return;
		listener.onItemVisible(item);
		ui.bind(this, listener, item, position);
	}

	@Override
	public int getItemCount() {
		return getVisiblePos(null);
	}

	I getReplyItem() {
		return replyItem;
	}

	public void setItems(Collection<I> items) {
		this.items.clear();
		this.items.addAll(items);
		notifyDataSetChanged();
	}

	public void add(I item) {
		items.add(item);
		addedItem = item;
		if (item.getParentId() == null) {
			notifyItemInserted(getVisiblePos(item));
		} else {
			// Try to find the item's parent and perform the proper ui update if
			// it's present and visible.
			for (int i = items.indexOf(item) - 1; i >= 0; i--) {
				I higherItem = items.get(i);
				if (higherItem.getLevel() < item.getLevel()) {
					// parent found
					if (higherItem.isShowingDescendants()) {
						int parentVisiblePos = getVisiblePos(higherItem);
						if (parentVisiblePos != NO_POSITION) {
							// parent is visible, we need to update its ui
							notifyItemChanged(parentVisiblePos);
							// new item insert ui
							int visiblePos = getVisiblePos(item);
							notifyItemInserted(visiblePos);
							break;
						}
					} else {
						// do not show the new item if its parent is not showing
						// descendants (this can be overridden by the user by
						// pressing the snack bar)
						break;
					}
				}
			}
		}
	}

	void scrollTo(I item) {
		int visiblePos = getVisiblePos(item);
		if (visiblePos == NO_POSITION && item.getParentId() != null) {
			// The item is not visible due to being hidden by its parent item.
			// Find the parent and make it visible and traverse up the parent
			// chain if necessary to make the item visible
			MessageId parentId = item.getParentId();
			for (int i = items.indexOf(item) - 1; i >= 0; i--) {
				I higherItem = items.get(i);
				if (higherItem.getId().equals(parentId)) {
					// parent found
					showDescendants(higherItem);
					int parentPos = getVisiblePos(higherItem);
					if (parentPos != NO_POSITION) {
						// parent or ancestor is visible, item's visibility
						// is ensured
						notifyItemChanged(parentPos);
						visiblePos = parentPos;
						break;
					}
					// parent or ancestor is hidden, we need to continue up the
					// dependency chain
					parentId = higherItem.getParentId();
				}
			}
		}
		if (visiblePos != NO_POSITION)
			layoutManager.scrollToPositionWithOffset(visiblePos, 0);
	}

	int getReplyCount(I item) {
		int counter = 0;
		int pos = items.indexOf(item);
		if (pos >= 0) {
			int ancestorLvl = item.getLevel();
			for (int i = pos + 1; i < items.size(); i++) {
				int descendantLvl = items.get(i).getLevel();
				if (descendantLvl <= ancestorLvl)
					break;
				if (descendantLvl == ancestorLvl + 1)
					counter++;
			}
		}
		return counter;
	}

	void setReplyItem(@Nullable I item) {
		if (replyItem != null) {
			notifyItemChanged(getVisiblePos(replyItem));
		}
		replyItem = item;
		if (replyItem != null) {
			notifyItemChanged(getVisiblePos(replyItem));
		}
	}

	void setReplyItemById(MessageId id) {
		for (I item : items) {
			if (item.getId().equals(id)) {
				setReplyItem(item);
				break;
			}
		}
	}

	private List<Integer> getSubTreeIndexes(int pos, int levelLimit) {
		List<Integer> indexList = new ArrayList<>();

		for (int i = pos + 1; i < getItemCount(); i++) {
			I item = getVisibleItem(i);
			if (item != null && item.getLevel() > levelLimit) {
				indexList.add(i);
			} else {
				break;
			}
		}
		return indexList;
	}

	public void showDescendants(I item) {
		item.setShowingDescendants(true);
		int visiblePos = getVisiblePos(item);
		List<Integer> indexList =
				getSubTreeIndexes(visiblePos, item.getLevel());
		if (!indexList.isEmpty()) {
			if (indexList.size() == 1) {
				notifyItemInserted(indexList.get(0));
			} else {
				notifyItemRangeInserted(indexList.get(0),
						indexList.size());
			}
		}
	}

	public void hideDescendants(I item) {
		int visiblePos = getVisiblePos(item);
		List<Integer> indexList =
				getSubTreeIndexes(visiblePos, item.getLevel());
		if (!indexList.isEmpty()) {
			// stop animating children
			for (int index : indexList) {
				ValueAnimator anim = animatingItems.get(items.get(index));
				if (anim != null && anim.isRunning()) {
					anim.cancel();
				}
			}
			if (indexList.size() == 1) {
				notifyItemRemoved(indexList.get(0));
			} else {
				notifyItemRangeRemoved(indexList.get(0),
						indexList.size());
			}
		}
		item.setShowingDescendants(false);
	}


	/**
	 * Returns the visible item at the given position
	 *
	 * @param position is visible item index
	 * @return the visible item at index 'position' from an ordered list of
	 * visible items, or null if 'position' is larger than the number of
	 * visible items.
	 */
	@Nullable
	public I getVisibleItem(int position) {
		int levelLimit = UNDEFINED;
		for (I item : items) {
			if (levelLimit >= 0) {
				// skip hidden items that their parent is hiding
				if (item.getLevel() > levelLimit) {
					continue;
				}
				levelLimit = UNDEFINED;
			}
			if (!item.isShowingDescendants()) {
				levelLimit = item.getLevel();
			}
			if (position-- == 0) {
				return item;
			}
		}
		return null;
	}

	boolean isVisible(I item) {
		return getVisiblePos(item) != NO_POSITION;
	}

	/**
	 * Returns the visible position of the given item.
	 *
	 * @param item the item to find the visible position of, or null to
	 *             return the total count of visible items.
	 * @return the visible position of 'item', or the total number of visible
	 * items if 'item' is null. If 'item' is not visible, NO_POSITION is
	 * returned.
	 */
	private int getVisiblePos(@Nullable I item) {
		int visibleCounter = 0;
		int levelLimit = UNDEFINED;
		for (I i : items) {
			if (levelLimit >= 0) {
				if (i.getLevel() > levelLimit) {
					// skip all the items below a non visible branch
					continue;
				}
				levelLimit = UNDEFINED;
			}
			if (item != null && item.equals(i)) {
				return visibleCounter;
			} else if (!i.isShowingDescendants()) {
				levelLimit = i.getLevel();
			}
			visibleCounter++;
		}
		return item == null ? visibleCounter : NO_POSITION;
	}

	I getAddedItem() {
		return addedItem;
	}

	void clearAddedItem() {
		addedItem = null;
	}

	void addAnimatingItem(I item, ValueAnimator anim) {
		animatingItems.put(item, anim);
	}

	void removeAnimatingItem(I item) {
		animatingItems.remove(item);
	}

	@Override
	public int getRevision() {
		return revision;
	}

	@UiThread
	@Override
	public void incrementRevision() {
		revision++;
	}

	protected interface ThreadItemListener<I> {

		void onItemVisible(I item);

		void onReplyClick(I item);
	}
}
