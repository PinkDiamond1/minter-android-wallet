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
package network.minter.bipwallet.sending.views

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.annotation.DrawableRes
import com.annimon.stream.Optional
import com.edwardstock.inputfield.form.InputWrapper
import com.google.common.base.MoreObjects.firstNonNull
import io.reactivex.BackpressureStrategy
import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import moxy.InjectViewState
import network.minter.bipwallet.R
import network.minter.bipwallet.addressbook.db.AddressBookRepository
import network.minter.bipwallet.addressbook.models.AddressContact
import network.minter.bipwallet.addressbook.ui.AddressBookActivity
import network.minter.bipwallet.analytics.AppEvent
import network.minter.bipwallet.apis.explorer.RepoTransactions
import network.minter.bipwallet.apis.reactive.ReactiveGate.createGateErrorPlain
import network.minter.bipwallet.apis.reactive.rxGate
import network.minter.bipwallet.internal.Wallet
import network.minter.bipwallet.internal.auth.AuthSession
import network.minter.bipwallet.internal.dialogs.ConfirmDialog
import network.minter.bipwallet.internal.dialogs.WalletProgressDialog
import network.minter.bipwallet.internal.helpers.KeyboardHelper
import network.minter.bipwallet.internal.helpers.MathHelper
import network.minter.bipwallet.internal.helpers.MathHelper.humanize
import network.minter.bipwallet.internal.helpers.MathHelper.normalize
import network.minter.bipwallet.internal.helpers.MathHelper.parseBigDecimal
import network.minter.bipwallet.internal.helpers.forms.validators.IsNotMnemonicValidator
import network.minter.bipwallet.internal.helpers.forms.validators.PayloadValidator
import network.minter.bipwallet.internal.mvp.MvpBasePresenter
import network.minter.bipwallet.internal.storage.RepoAccounts
import network.minter.bipwallet.internal.storage.SecretStorage
import network.minter.bipwallet.internal.storage.models.AddressListBalancesTotal
import network.minter.bipwallet.internal.system.SimpleTextWatcher
import network.minter.bipwallet.sending.account.selectorDataFromAccounts
import network.minter.bipwallet.sending.adapters.RecipientListAdapter
import network.minter.bipwallet.sending.contract.SendView
import network.minter.bipwallet.sending.ui.QRCodeScannerActivity
import network.minter.bipwallet.sending.ui.SendTabFragment
import network.minter.bipwallet.sending.ui.dialogs.TxSendStartDialog
import network.minter.bipwallet.sending.ui.dialogs.TxSendSuccessDialog
import network.minter.bipwallet.tx.contract.TxInitData
import network.minter.bipwallet.wallets.selector.WalletItem
import network.minter.blockchain.models.BCResult
import network.minter.blockchain.models.TransactionCommissionValue
import network.minter.blockchain.models.TransactionSendResult
import network.minter.blockchain.models.operational.*
import network.minter.core.MinterSDK.DEFAULT_COIN
import network.minter.core.crypto.MinterAddress
import network.minter.core.crypto.MinterPublicKey
import network.minter.core.crypto.PrivateKey
import network.minter.explorer.models.CoinBalance
import network.minter.explorer.models.GasValue
import network.minter.explorer.models.GateResult
import network.minter.explorer.models.TxCount
import network.minter.explorer.repo.*
import network.minter.ledger.connector.rxjava2.RxMinterLedger
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigDecimal.ZERO
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * minter-android-wallet. 2018
 * @author Eduard Maximovich (edward.vstock@gmail.com)
 */

@InjectViewState
class SendTabPresenter @Inject constructor() : MvpBasePresenter<SendView>() {
    companion object {
        private const val REQUEST_CODE_QR_SCAN_ADDRESS = 101
        private const val REQUEST_CODE_ADDRESS_BOOK_SELECT = 102
        private val PAYLOAD_FEE = BigDecimal.valueOf(0.002)
    }

    @Inject lateinit var secretStorage: SecretStorage
    @Inject lateinit var session: AuthSession
    @Inject lateinit var cachedTxRepo: RepoTransactions
    @Inject lateinit var accountStorage: RepoAccounts
    @Inject lateinit var coinRepo: ExplorerCoinsRepository
    @Inject lateinit var validatorsRepo: ExplorerValidatorsRepository
    @Inject lateinit var gasRepo: GateGasRepository
    @Inject lateinit var estimateRepo: GateEstimateRepository
    @Inject lateinit var gateTxRepo: GateTransactionRepository
    @Inject lateinit var addressBookRepo: AddressBookRepository

    private var mFromAccount: CoinBalance? = null
    private var mAmount: BigDecimal? = null
    private var mRecipient: AddressContact? = null
    private var mAvatar: String? = null

    @DrawableRes
    private var mAvatarRes = 0
    private val mUseMax = AtomicBoolean(false)
    private val mClickedUseMax = AtomicBoolean(false)
    private var mInputChange: BehaviorSubject<String>? = null
    private var mAddressChange: BehaviorSubject<String>? = null
    private var mGasCoin = DEFAULT_COIN
    private var mGasPrice = BigInteger("1")
    private var mLastAccount: CoinBalance? = null
    private var mSendFee: BigDecimal? = null
    private var mPayload: ByteArray? = null
    private val mPayloadChangeListener: TextWatcher = object : SimpleTextWatcher() {
        override fun afterTextChanged(s: Editable) {
            var tmpPayload = s.toString().toByteArray(StandardCharsets.UTF_8)
            val totalBytes = tmpPayload.size
            if (totalBytes > PayloadValidator.MAX_PAYLOAD_LENGTH) {
                val tmp = ByteArray(1024)
                System.arraycopy(tmpPayload, 0, tmp, 0, 1024)
                viewState.setPayload(String(tmp, StandardCharsets.UTF_8))
                tmpPayload = tmp
            }
            mPayload = tmpPayload
            val validator = IsNotMnemonicValidator("""
    ATTENTION: You are about to send seed phrase in the message attached to this transaction.

    If you do this, anyone will be able to see it and access your funds!
    """.trimIndent(), false)
            validator.validate(String(mPayload!!))
                    .subscribe { res: Boolean? ->
                        if (!res!!) {
                            viewState.setPayloadError(validator.errorMessage)
                        } else {
                            viewState.setPayloadError(null)
                        }
                    }
            setupFee()
        }
    }
    private var mFormValid = false
    override fun attachView(view: SendView) {
        super.attachView(view)
        viewState.setOnClickAccountSelectedListener(View.OnClickListener { onClickAccountSelector() })
        viewState.setOnTextChangedListener { input, valid ->
            onInputTextChanged(input, valid)
        }
        viewState.setOnSubmit(View.OnClickListener { onSubmit() })
        viewState.setOnClickMaximum(View.OnClickListener { v -> onClickMaximum(v) })
        viewState.setOnClickAddPayload(View.OnClickListener { v -> onClickAddPayload(v) })
        viewState.setOnClickClearPayload(View.OnClickListener { onClickClearPayload() })
        viewState.setPayloadChangeListener(mPayloadChangeListener)
        viewState.setOnContactsClickListener(View.OnClickListener { onClickContacts() })
        viewState.setRecipientAutocompleteItemClickListener(RecipientListAdapter.OnItemClickListener { contact: AddressContact, pos: Int ->
            onAutocompleteSelected(contact, pos)
        })
        loadAndSetFee()
        accountStorage.update()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) {
            return
        }
        //todo: refactor below
        if (requestCode == REQUEST_CODE_QR_SCAN_ADDRESS) {
            if (data != null && data.hasExtra(QRCodeScannerActivity.RESULT_TEXT)) {
                //Getting the passed result
                val result = data.getStringExtra(QRCodeScannerActivity.RESULT_TEXT)
                if (result != null) {
                    val isMxAddress = result.matches(MinterAddress.ADDRESS_PATTERN.toRegex())
                    val isMpAddress = result.matches(MinterPublicKey.PUB_KEY_PATTERN.toRegex())
                    mRecipient!!.address = result
                    if (isMxAddress) {
                        mRecipient!!.type = AddressContact.AddressType.Address
                        viewState.setRecipient(mRecipient!!)
                    } else if (isMpAddress) {
                        mRecipient!!.type = AddressContact.AddressType.ValidatorPubKey
                        viewState.setRecipient(mRecipient!!)
                    }
                }
            }
        } else if (requestCode == SendTabFragment.REQUEST_CODE_QR_SCAN_TX) {
            val result = data?.getStringExtra(QRCodeScannerActivity.RESULT_TEXT)
            if (result != null) {
                val isMxAddress = result.matches(MinterAddress.ADDRESS_PATTERN.toRegex())
                val isMpAddress = result.matches(MinterPublicKey.PUB_KEY_PATTERN.toRegex())
                if (isMxAddress || isMpAddress) {
                    onActivityResult(REQUEST_CODE_QR_SCAN_ADDRESS, resultCode, data)
                    return
                }
                try {
                    viewState.startExternalTransaction(result)
                } catch (t: Throwable) {
                    Timber.w(t, "Unable to parse remote transaction: %s", result)
                    viewState.startDialog { ctx ->
                        ConfirmDialog.Builder(ctx, "Unable to scan QR")
                                .setText("Invalid transaction data: %s", t.message)
                                .setPositiveAction(R.string.btn_close)
                                .create()
                    }
                }
            }
        } else if (requestCode == REQUEST_CODE_ADDRESS_BOOK_SELECT) {
            val contact = AddressBookActivity.getResult(data!!) ?: return
            mRecipient = contact
            viewState.setRecipient(mRecipient!!)
        }
    }

    override fun onFirstViewAttach() {
        super.onFirstViewAttach()

        accountStorage.observe()
                .joinToUi()
                .subscribe(
                        { res: AddressListBalancesTotal ->
                            if (!res.isEmpty) {
                                viewState.setWallets(WalletItem.create(secretStorage, res))
                                viewState.setMainWallet(WalletItem.create(secretStorage, res.getBalance(secretStorage.mainWallet)))
                                val acc = accountStorage.entity.mainWallet
                                if (mLastAccount != null) {
                                    onAccountSelected(acc.findCoinByName(mLastAccount!!.coin).orElse(acc.coinsList[0]))
                                } else {
                                    onAccountSelected(acc.coinsList[0])
                                }
                            }
                        },
                        { t: Throwable ->
                            viewState.onError(t)
                        }
                )
                .disposeOnDestroy()

        mInputChange = BehaviorSubject.create()
        mAddressChange = BehaviorSubject.create()

        mInputChange!!
                .toFlowable(BackpressureStrategy.LATEST)
                .debounce(200, TimeUnit.MILLISECONDS)
                .subscribe { amount: String? -> onAmountChanged(amount) }
                .disposeOnDestroy()

        mAddressChange!!
                .toFlowable(BackpressureStrategy.LATEST)
                .debounce(200, TimeUnit.MILLISECONDS)
                .subscribe { address: String -> onAddressChanged(address) }
                .disposeOnDestroy()

        checkEnableSubmit()

        viewState.setFormValidationListener {
            mFormValid = it
            Timber.d("Form is valid: %b", it)
            checkEnableSubmit()
        }
    }

    private fun onClickClearPayload() {
        viewState.hidePayload()
    }

    private fun onClickContacts() {
        viewState.startAddressBook(REQUEST_CODE_ADDRESS_BOOK_SELECT)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onClickAddPayload(view: View) {
        viewState.showPayload()
    }

    private fun loadAndSetFee() {
        gasRepo.minGas.rxGate()
                .subscribeOn(Schedulers.io())
                .toFlowable(BackpressureStrategy.LATEST)
                .debounce(200, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { res: GateResult<GasValue> ->
                            if (res.isOk) {
                                mGasPrice = res.result.gas
                                Timber.d("Min Gas price: %s", mGasPrice.toString())
                                setupFee()
                            }
                        },
                        { e: Throwable? ->
                            Timber.w(e)
                        }
                )
    }

    private fun setupFee() {
        mSendFee = when (transactionTypeByAddress) {
            OperationType.Delegate -> OperationType.Delegate.fee.multiply(BigDecimal(mGasPrice))
            OperationType.SendCoin -> OperationType.SendCoin.fee.multiply(BigDecimal(mGasPrice))
            else -> ZERO
        }
        mSendFee = mSendFee!! + payloadFee
        val fee: String = String.format("%s %s", mSendFee!!.humanize(), DEFAULT_COIN)
        viewState.setFee(fee)
    }

    private val payloadFee: BigDecimal
        get() = BigDecimal.valueOf(firstNonNull(mPayload, ByteArray(0)).size.toLong()).multiply(PAYLOAD_FEE)

    private fun checkEnoughBalance(amount: BigDecimal): Boolean {
        if (mFromAccount!!.coin!!.toLowerCase() != DEFAULT_COIN.toLowerCase()) {
            return true
        }
        val enough = MathHelper.bdGTE(amount, OperationType.SendCoin.fee)
        if (!enough) {
            viewState.setAmountError("Insufficient balance")
        } else {
            viewState.setAmountError(null)
        }
        return enough
    }

    /**
     * Checks input amount is not empty and not negative number
     * @param amount
     * @return
     */
    private fun checkAmountIsValid(amount: String?): Boolean {
        if (amount == null || amount.isEmpty()) {
            viewState.setAmountError("Amount can't be empty")
            return false
        }
        val valid = amount.parseBigDecimal() > ZERO
        viewState.setAmountError(if (!valid) "Amount must be greater or equals to 0" else null)
        return valid
    }

    private fun onAmountChanged(amount: String?) {
        mAmount = amount.parseBigDecimal()

        if (!mClickedUseMax.get()) {
            mUseMax.set(false)
        }

        mClickedUseMax.set(false)
        checkAmountIsValid(amount)
        //        checkEnableSubmit();
        loadAndSetFee()
        checkEnableSubmit()
    }

    private fun onAddressChanged(address: String) {
        if (address.isEmpty()) viewState.setFee("") else setupFee()
    }

    private fun onSubmit() {
        if (mRecipient == null) {
            viewState.setRecipientError("Recipient required")
            return
        }
        if (mAmount == null) {
            viewState.setAmountError("Amount must be set")
            return
        }
        analytics.send(AppEvent.SendCoinsSendButton)
        when (transactionTypeByAddress) {
            OperationType.Delegate -> {
                mAvatar = null
                mAvatarRes = R.drawable.img_avatar_delegate
                startSendDialog()
            }
            OperationType.SendCoin -> {
                mAvatar = mRecipient!!.avatar
                startSendDialog()
            }
            else -> {
            }
        }
    }

    private val transactionTypeByAddress: OperationType
        get() {
            if (mRecipient == null) {
                return OperationType.SendCoin
            }
            return if (mRecipient!!.type == AddressContact.AddressType.ValidatorPubKey) OperationType.Delegate else OperationType.SendCoin
        }

    private fun startSendDialog() {

        viewState.startDialog { ctx ->
            try {
                analytics.send(AppEvent.SendCoinPopupScreen)

                val dialog = TxSendStartDialog.Builder(ctx, R.string.tx_send_overall_title)
                        .setAmount(mAmount)
                        .setRecipientName(mRecipient!!.name)
                        .setCoin(mFromAccount!!.coin)
                        .setPositiveAction(R.string.btn_send) { d, _ ->
                            Wallet.app().sounds().play(R.raw.bip_beep_digi_octave)
                            onStartExecuteTransaction()
                            analytics.send(AppEvent.SendCoinPopupSendButton)
                            d.dismiss()
                        }
                        .setNegativeAction(R.string.btn_cancel) { d, _ ->
                            Wallet.app().sounds().play(R.raw.cancel_pop_hi)
                            analytics.send(AppEvent.SendCoinPopupCancelButton)
                            d.dismiss()
                        }
                        .create()
                dialog.setCancelable(true)

                dialog
            } catch (badState: NullPointerException) {
                ConfirmDialog.Builder(ctx, R.string.error)
                        .setText(badState.message)
                        .setPositiveAction(R.string.btn_close)
                        .create()
            }
        }
    }

    private fun checkEnableSubmit() {
        if (mFromAccount == null) {
            Timber.d("Account did not loaded yet!")
            viewState.setSubmitEnabled(false)
            return
        }

        Timber.d("Amount did set and it's NOT a NULL")
        val a = MathHelper.bdGTE(mAmount, ZERO)
        val b = mFormValid
        val c = checkEnoughBalance(mFromAccount!!.amount)
        val formFullyValid = a && b && c
        viewState.setSubmitEnabled(formFullyValid)
    }

    private fun onClickMaximum(view: View?) {
        if (mFromAccount == null) {
            viewState.setCommonError("Account didn't loaded yet...")
            return
        }
        mUseMax.set(true)
        mClickedUseMax.set(true)
        mAmount = mFromAccount!!.amount
        //        checkEnableSubmit();
        viewState.setAmount(mFromAccount!!.amount.stripTrailingZeros().toPlainString())
        analytics.send(AppEvent.SendCoinsUseMaxButton)
        if (view != null && view.context is Activity) {
            KeyboardHelper.hideKeyboard(view.context as Activity)
        }
    }

    private fun findAccountByCoin(coin: String): Optional<CoinBalance> {
        return accountStorage.entity.mainWallet.findCoinByName(coin)
    }

    @Throws(OperationInvalidDataException::class)
    private fun createPreTx(type: OperationType): TransactionSign {
        val preTx: Transaction
        val builder = Transaction.Builder(BigInteger("1"))
                .setGasCoin(mGasCoin)
                .setGasPrice(mGasPrice)

        if (mPayload != null && mPayload!!.isNotEmpty()) {
            builder.setPayload(mPayload)
        }
        preTx = if (type == OperationType.Delegate) {
            builder
                    .delegate()
                    .setCoin(mFromAccount!!.coin)
                    .setPublicKey(mRecipient!!.address)
                    .setStake(mAmount)
                    .build()
        } else {
            builder
                    .sendCoin()
                    .setCoin(mFromAccount!!.coin)
                    .setTo(mRecipient!!.address)
                    .setValue(mAmount)
                    .build()
        }
        val dummyPrivate = PrivateKey("F000000000000000000000000000000000000000000000000000000000000000")
        return preTx.signSingle(dummyPrivate)
    }

    @Throws(OperationInvalidDataException::class)
    private fun createFinalTx(nonce: BigInteger, type: OperationType, amountToSend: BigDecimal?): Transaction {
        val tx: Transaction
        val builder = Transaction.Builder(nonce)
                .setGasCoin(mGasCoin)
                .setGasPrice(mGasPrice)
        if (mPayload != null && mPayload!!.isNotEmpty()) {
            builder.setPayload(mPayload)
        }
        tx = if (type == OperationType.Delegate) {
            builder
                    .delegate()
                    .setCoin(mFromAccount!!.coin)
                    .setPublicKey(mRecipient!!.address)
                    .setStake(amountToSend)
                    .build()
        } else {
            builder
                    .sendCoin()
                    .setCoin(mFromAccount!!.coin)
                    .setTo(mRecipient!!.address)
                    .setValue(amountToSend)
                    .build()
        }
        return tx
    }

    private val fee: BigDecimal
        get() = transactionTypeByAddress.fee + payloadFee

    private val feeNormalized: BigInteger
        get() = (fee * Transaction.VALUE_MUL_DEC).normalize()

    operator fun BigDecimal.compareTo(value: Int): Int {
        return this.compareTo(BigDecimal(value.toString()))
    }

    /**
     * This is a complex sending method, read carefully, almost each line is commented, i don't know how
     * to simplify all of this
     *
     *
     * Base logic in that, if we have enough BIP to send amount to your friend and you have some additional
     * value on your account to pay fee, it's ok. But you can have enough BIP to send, but not enough to pay fee.
     * Also, as we are minting coins, user can have as much coins as Minter's blockchain have.
     * So user can send his CUSTOM_COIN to his friend and don't have enough BIP to pay fee, we must switch GAS_COIN
     * to user's custom coin, or vice-versa.
     *
     *
     * So first: we detecting what account used
     * Second: calc balance on it, and compare with input amount
     * Third: if user wants to send CUSTOM_COIN, we don't know the price of it, and we should ask node about it,
     * so, we're creating preliminary transaction and requesting "estimate transaction commission"
     * Next: combining all prepared data and finalizing our calculation. For example, if your clicked "use max",
     * we must subtract commission sum from account balance.
     * Read more in body..
     */
    private fun onStartExecuteTransaction() {

        viewState.startDialog { ctx: Context? ->
            val dialog = WalletProgressDialog.Builder(ctx, R.string.tx_send_in_progress)
                    .setText(R.string.please_wait)
                    .create()
            dialog.setCancelable(false)

            // BIP account exists anyway, no need
            val baseAccount = findAccountByCoin(DEFAULT_COIN).get()
            val sendAccount = mFromAccount
            val isBaseAccount = sendAccount!!.coin == DEFAULT_COIN
            val type = transactionTypeByAddress
            val enoughBaseForFee: Boolean

            // default coin for pay fee - MNT (base coin)
            val txFeeValue = GateResult<TransactionCommissionValue>()
            txFeeValue.result = TransactionCommissionValue()
            txFeeValue.result.value = feeNormalized
            enoughBaseForFee = baseAccount.amount > fee
            var txFeeValueResolver: Observable<GateResult<TransactionCommissionValue>> = Observable.just(txFeeValue)
            val txNonceResolver = estimateRepo.getTransactionCount(mFromAccount!!.address!!).rxGate()

            // if enough balance on base BIP account, set it as gas coin
            if (enoughBaseForFee) {
                Timber.tag("TX Send").d("Using base coin commission %s", DEFAULT_COIN)
                mGasCoin = baseAccount.coin!!
            } else if (!isBaseAccount) {
                Timber.tag("TX Send").d("Not enough balance in %s to pay fee, using %s coin", DEFAULT_COIN, sendAccount.coin)
                mGasCoin = sendAccount.coin!!
                // otherwise getting
                Timber.tag("TX Send").d("Resolving REAL fee value in custom coin %s relatively to base coin", mFromAccount!!.coin)
                // resolving fee currency for custom currency
                // creating tx
                try {
                    val preSign = createPreTx(type)
                    txFeeValueResolver = estimateRepo.getTransactionCommission(preSign).rxGate()
                } catch (e: OperationInvalidDataException) {
                    Timber.w(e)
                    val commissionValue = GateResult<TransactionCommissionValue>()
                    txFeeValue.result.value = feeNormalized
                    txFeeValueResolver = Observable.just(commissionValue)
                }
            }

            // creating preparation result to send transaction
            Observable
                    .combineLatest(txFeeValueResolver, txNonceResolver, BiFunction { t1: GateResult<TransactionCommissionValue>, t2: GateResult<TxCount> -> TxInitData(t1, t2) })
                    .switchMap { txInitData: TxInitData ->
                        // if in previous request we've got error, returning it
                        if (!txInitData.isSuccess) {
                            return@switchMap Observable.just(GateResult.copyError<TransactionSendResult>(txInitData.errorResult))
                        }
                        val amountToSend: BigDecimal

                        // don't calc fee if enough balance in base coin and we are sending not a base coin (MNT or BIP)
                        if (enoughBaseForFee && !isBaseAccount) {
                            txInitData.commission = ZERO
                        }

                        // if balance enough to send required sum + fee, do nothing
                        // (mAmount + txInitData.commission) <= mFromAccount.getBalance()

                        if (mFromAccount!!.amount >= (mAmount!! + txInitData.commission!!)) {
                            Timber.tag("TX Send").d("Don't change sending amount - balance enough to send")
                            amountToSend = mAmount!!
                        } else {
                            if (!mUseMax.get()) {
                                txInitData.commission = ZERO
                            }
                            amountToSend = mAmount!! - txInitData.commission!!
                            Timber.tag("TX Send").d("Subtracting sending amount (-%s): balance not enough to send", txInitData.commission)
                        }


                        // if after subtracting fee from sending sum has become less than account balance at all, returning error with message "insufficient funds"
                        // although, this case must handles the blockchain node, nevertheless we handle it to show user more friendly error
                        // amountToSend < 0
                        //bdLT(amountToSend, 0.0)

                        if (amountToSend < 0) {
                            // follow the my guideline, return result instead of throwing error, it's easily to handle errors
                            // creating error result, in it we'll write error message with required sum
                            val errorRes: GateResult<TransactionSendResult>
                            val balanceMustBe = mAmount!! + txInitData.commission!!
                            // this means user sending less than his balance, but it's still not enough to pay fee
                            // mAmount < mFromAccount.getAmount()
                            // if (bdLT(mAmount, mFromAccount!!.getAmount()))
                            errorRes = if (mAmount!! < mFromAccount!!.amount) {
                                // special for humans - calculate how much balance haven't enough balance
                                val notEnough = txInitData.commission!! - (mFromAccount!!.amount - mAmount!!)
                                Timber.tag("TX Send").d("Amount: %s, fromAcc: %s, diff: %s",
                                        mAmount!!.humanize(),
                                        mFromAccount!!.amount.humanize(),
                                        notEnough.humanize()
                                )
                                createGateErrorPlain(
                                        String.format("Insufficient funds: not enough %s %s, wanted: %s %s",
                                                notEnough.humanize(),
                                                mFromAccount!!.coin,
                                                balanceMustBe.humanize(),
                                                mFromAccount!!.coin
                                        ),
                                        BCResult.ResultCode.InsufficientFunds.value,
                                        400
                                )
                            } else {
                                // sum bigger than account balance, so, just show full required sum
                                Timber.tag("TX Send").d("Amount: %s, fromAcc: %s, diff: %s",
                                        mAmount!!.humanize(),
                                        mFromAccount!!.amount.humanize(),
                                        balanceMustBe.humanize()
                                )
                                createGateErrorPlain(
                                        String.format("Insufficient funds: wanted %s %s",
                                                balanceMustBe.humanize(),
                                                mFromAccount!!.amount
                                        ),
                                        BCResult.ResultCode.InsufficientFunds.value,
                                        400
                                )
                            }
                            return@switchMap Observable.just(errorRes)
                        }

                        return@switchMap signSendTx(dialog, txInitData.nonce!!, type, amountToSend)
                    }
                    .doFinally { onExecuteComplete() }
                    .joinToUi()
                    .subscribe(
                            { result: GateResult<TransactionSendResult> ->
                                onSuccessExecuteTransaction(result)
                            },
                            { throwable: Throwable ->
                                onFailedExecuteTransaction(throwable)
                            }
                    )
                    .disposeOnDestroy()

            dialog
        }
    }

    @Throws(OperationInvalidDataException::class)
    private fun signSendTx(dialog: WalletProgressDialog, nonce: BigInteger, type: OperationType, amountToSend: BigDecimal?): ObservableSource<GateResult<TransactionSendResult>> {
        // creating tx
        val tx = createFinalTx(nonce.add(BigInteger.ONE), type, amountToSend)

        // if user created account with ledger, use it to sign tx
        return if (session.role == AuthSession.AuthType.Hardware) {
            dialog.setText("Please, compare transaction hashes: %s", tx.unsignedTxHash)
            Timber.d("Unsigned tx hash: %s", tx.unsignedTxHash)
            signSendTxExternally(dialog, tx)
        } else {
            // old school signing
            signSendTxInternally(tx)
        }
    }

    private fun signSendTxInternally(tx: Transaction): ObservableSource<GateResult<TransactionSendResult>> {
        val data = secretStorage.getSecret(mFromAccount!!.address!!)
        val sign = tx.signSingle(data.privateKey)
        return gateTxRepo.sendTransaction(sign).rxGate().joinToUi()
    }

    private fun signSendTxExternally(dialog: WalletProgressDialog, tx: Transaction): ObservableSource<GateResult<TransactionSendResult>> {
        val devInstance = Wallet.app().ledger()
        if (!devInstance.isReady) {
            dialog.setText("Connect ledger and open Minter Application")
        }
        return RxMinterLedger
                .initObserve(devInstance)
                .flatMap { dev: RxMinterLedger ->
                    dialog.setText("Compare hashes: " + tx.unsignedTxHash.toHexString())
                    dev.signTxHash(tx.unsignedTxHash)
                }
                .toObservable()
                .switchMap { signatureSingleData: SignatureSingleData? ->
                    val sign = tx.signExternal(signatureSingleData)
                    dialog.setText(R.string.tx_send_in_progress)
                    gateTxRepo.sendTransaction(sign).rxGate().joinToUi()
                }
                .doFinally { devInstance.destroy() }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
    }

    private fun onExecuteComplete() {
    }

    private fun onFailedExecuteTransaction(throwable: Throwable) {
        Timber.w(throwable, "Uncaught tx error")
        viewState.startDialog { ctx ->
            ConfirmDialog.Builder(ctx, "Unable to send transaction")
                    .setText(throwable.message)
                    .setPositiveAction("Close")
                    .create()
        }
    }

    private fun onErrorExecuteTransaction(errorResult: GateResult<*>) {
        Timber.e(errorResult.message, "Unable to send transaction")
        viewState.startDialog { ctx ->
            ConfirmDialog.Builder(ctx, "Unable to send transaction")
                    .setText(errorResult.message)
                    .setPositiveAction("Close")
                    .create()
        }
    }

    private fun onSuccessExecuteTransaction(result: GateResult<TransactionSendResult>) {
        if (!result.isOk) {
            onErrorExecuteTransaction(result)
            return
        }
        viewState.hidePayload()
        accountStorage.update(true)
        cachedTxRepo.update(true)
        viewState.startDialog { ctx ->
            analytics.send(AppEvent.SentCoinPopupScreen)
            val builder = TxSendSuccessDialog.Builder(ctx)
                    .setLabel(R.string.tx_send_success_dialog_description)
                    .setValue(mRecipient!!.name)
                    .setPositiveAction("View transaction") { d, _ ->
                        Wallet.app().sounds().play(R.raw.click_pop_zap)
                        viewState.startExplorer(result.result.txHash.toString())
                        d.dismiss()
                        analytics.send(AppEvent.SentCoinPopupViewTransactionButton)
                    }
                    .setNegativeAction("Close") { d: DialogInterface, _: Int ->
                        d.dismiss()
                        analytics.send(AppEvent.SentCoinPopupCloseButton)
                    }

            if (mRecipient != null && mRecipient!!.id == 0 && mRecipient!!.address != null) {
                val recipientAddress = mRecipient!!.address!!
                builder.setNeutralAction("Save This Address") { d, _ ->
                    viewState.startAddContact(recipientAddress)
                    d.dismiss()
                }
            }
            builder.create()
        }
        viewState.clearInputs()
        mRecipient = null
        mSendFee = null
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onInputTextChanged(editText: InputWrapper, valid: Boolean) {
        val s = editText.text.toString()
        when (editText.id) {
            R.id.input_recipient -> {
                addressBookRepo
                        .findByNameOrAddress(s)
                        .subscribe(
                                { res: AddressContact? ->
                                    mRecipient = res
                                    mAddressChange!!.onNext(mRecipient!!.name!!)
                                    viewState.hideAutocomplete()
                                },
                                { t: Throwable? ->
                                    mRecipient = null
                                    viewState.setSubmitEnabled(false)
                                    addressBookRepo
                                            .findSuggestionsByNameOrAddress(s)
                                            .subscribe(
                                                    { suggestions: List<AddressContact> ->
                                                        viewState.setRecipientAutocompleteItems(suggestions)
                                                    },
                                                    { t2: Throwable? ->
                                                        Timber.w(t)
                                                        Timber.w(t2)
                                                    }
                                            )
                                })
                        .disposeOnDestroy()
            }
            R.id.input_amount -> mInputChange!!.onNext(editText.text.toString())
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onAutocompleteSelected(contact: AddressContact, pos: Int) {
        viewState.setRecipient(contact)
    }

    private fun onClickAccountSelector() {
        analytics.send(AppEvent.SendCoinsChooseCoinButton)
        viewState.startAccountSelector(
                selectorDataFromAccounts(accountStorage.entity.mainWallet.coinsList)
        ) { onAccountSelected(it.data) }
    }

    private fun onAccountSelected(coinAccount: CoinBalance) {
        mFromAccount = coinAccount
        mLastAccount = coinAccount
        viewState.setAccountName(String.format("%s (%s)", coinAccount.coin?.toUpperCase(), coinAccount.amount.humanize()))
    }
}