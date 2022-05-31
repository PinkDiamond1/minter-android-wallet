/*
 * Copyright (C) by MinterTeam. 2022
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

package network.minter.bipwallet.addressbook.contract

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import network.minter.bipwallet.addressbook.models.AddressContact
import network.minter.bipwallet.addressbook.ui.AddressBookActivity
import org.parceler.Parcels

class GetAddressBookContact: ActivityResultContract<Unit, AddressContact?>() {
    override fun createIntent(context: Context, input: Unit?): Intent {
        return Intent(context, AddressBookActivity::class.java)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): AddressContact? {
        return if(resultCode == Activity.RESULT_OK) {
            if (intent != null && !intent.hasExtra(AddressBookActivity.EXTRA_CONTACT)) {
                null
            } else {
                Parcels.unwrap<AddressContact>(intent?.getParcelableExtra(AddressBookActivity.EXTRA_CONTACT))
            }
        } else null
    }
}