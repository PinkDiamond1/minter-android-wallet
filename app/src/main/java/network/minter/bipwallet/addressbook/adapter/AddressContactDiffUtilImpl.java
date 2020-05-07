/*
 * Copyright (C) by MinterTeam. 2020
 * @link <a href="https://github.com/MinterTeam">Org Github</a>
 * @link <a href="https://github.com/edwardstock">Maintainer Github</a>
 *
 * The MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package network.minter.bipwallet.addressbook.adapter;

import java.util.List;

import androidx.recyclerview.widget.DiffUtil;
import network.minter.bipwallet.addressbook.models.AddressBookItem;

public class AddressContactDiffUtilImpl extends DiffUtil.Callback {
    private List<AddressBookItem> mOldItems, mNewItems;

    public AddressContactDiffUtilImpl(List<AddressBookItem> oldItems, List<AddressBookItem> newItems) {
        mOldItems = oldItems;
        mNewItems = newItems;
    }

    @Override
    public int getOldListSize() {
        return mOldItems.size();
    }

    @Override
    public int getNewListSize() {
        return mNewItems.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        final AddressBookItem oldItem = mOldItems.get(oldItemPosition);
        final AddressBookItem newItem = mNewItems.get(newItemPosition);

        return oldItem.isSameOf(newItem);
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        final AddressBookItem oldItem = mOldItems.get(oldItemPosition);
        final AddressBookItem newItem = mNewItems.get(newItemPosition);

        return oldItem.equals(newItem);
    }
}
