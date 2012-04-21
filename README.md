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
    
    The transaction object is encoded as a byte stream and recorded
    in a special row `transaction.<timestamp>.uid`. Column `buffer`
    encodes the transaction object. The column `status` encodes the
    global status of the transaction.

Set status to `0-WRITTEN`

2. Lock all keys listed by mutations or assertions
    
    Keys are locked by a checkAndPut to a special lock column.
    Given any key `x` the lock column is `x_lock`. 
    Any value means the column is locked. The tag value should be 
    the uid of the transactions row. Transactions must respect existing
    locks and either wait or abort in such a case.

    Set the global status to `1-LOCKED`

3. Run all assertions
    
    Remember that any asserted cells must be locked in the previous
    stage. Assertions that fail must abort the transaction.

    Set the global status to `2-ASSERTED`

4. Mutate values
    
    By now, if the transaction should halt due to a crash, recovery
    software should complete the transaction during recovery.

    Since HBase never over-writes old values, data is never really lost
    except during a compactions. Just for safety, tag the old version 
    by putting its timestamp into `x_tag`.

    Put the new value.

    Once all values are inserted, set the global status to `3-PUT`

5. Cleanup
	
    The transaction is done, but the safety measures must be cleaned
    up gracefully.

    Clear all `x_tag` values then set the global status to `4-CLEANING`.
	
6. Unlock
	
    Unlock all the cells. An unloced cell sboudl be immediately available  
    for another transaction, even before the rest of the cells are unlocked.

    After all tags are unlocked, set the global status to `5-COMPLETE`

Cleanup of transaction rows can be done at the discression of the administrators.

## Discussion ##

I'm not sure if the `x_tag` is necessary. Certainly HBase makes it easier
to recover from transactions by never deleting old data. 

I'd like to point out again that this is not intended to be a high-performance
addition to HBase. Rather, should you build 90% of your application in HBase
then decide a small piece needs some transactional security, this is an easy
option to throw in the mix. Don't use it unless it's necessary.

I think any good implementation will require a `fsck` that can be run 
post crash. That should be added into the coprocessor at some point.
