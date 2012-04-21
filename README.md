This builds a transactional layer into HBase via coprocessors.

Transactions can be performed by sending a group of mutations,
to a coprocessor. The coprocessor modifies the table using a
special mutation protocol that ensures anyone using the same
protocol will only view atomic commits.

Querying the table directly does not guaruntee atomicity. You
_must_ query using the provided protocol.

The protocol guaruntees that, should a crash occur, partial
transactions can be recovered.

The goal of this protocol is _not_ performance. It is an extra
layer of guaruntees should they be required.

## Mutation Protocol ##

The mutation protocol is as follows:

1. The entire transaction is pre-written
    
    The transaction is encoded as a byte stream and recorded
    in a special row `transaction.<timestamp>` with two columns,
    `buffer` and `status`.
    
    The column `buffer` holds the transaction object, while `status` 
    holds the global status of the transaction.
    
    After the transaction is written, the global status is set to `0-WRITTEN`

2. Lock all keys listed by mutations or assertions
    
    Keys are locked by a checkAndPut to a special lock column.
    Given any column-key `x` the lock column is `x_lock`.
    Any value in `x_lock` indicates the column is locked. 
    Transactions should write their timestamp to the lock column,
    so a locked cell can always be associated to a transaction.
    Transactions must respect other locks and either wait or abort 
    when required lock is encountered.

    After all locks are set change the global status to `1-LOCKED`

3. Run all assertions
    
    Remember that a transaction must lock any cells required by
    the list of `Assertion`s.
    Should any assertions fail the transaction must aborted, and 
    all locks released safely.

    If all assertions succeed, set the global status to `2-ASSERTED`

4. Mutate values
    
    By now, if the transaction should halt due to a crash, recovery
    software should complete the transaction during recovery.
    
    Apply all mutations.
    
    Non-crash errors that occur at this stage, such as IOExceptions
    the transaction must still be completed. Although it is possibly
    to recover the old atomic state.
    
    Once all values are mutated, set the global status to `3-MUTATED`
	
5. Unlock
	
    Unlock all the cells. An unlocked cell should be immediately available  
    for another transaction, even before the rest of the cells are unlocked.

    After all tags are unlocked, set the global status to `4-COMPLETED`

Cleanup of transaction rows can be done at the discretion of the administrators.

## Discussion ##

I'd like to point out again that this is not intended to be a high-performance
addition to HBase. Rather, should you build 90% of your application in HBase
then decide a small piece needs some transactional security, this is an easy
option to throw in the mix. Don't use it unless it's necessary.

I think any good implementation will require a `fsck` that can be run 
post crash. That should be added into the coprocessor at some point.
