***Ordered stream and Joins ***

In very different tasks we have not just stream (flows) of data, but
ordered by some meanings streams. It may be naturally ordered by some id
or temporally (like log of event records). We can benefit a lot using
this knowledge – spare memory or significantly increase the performance.

This small library is about operating with the streams (Akka Flows) of
ordered elements.

Here “Ordered” means the sequence of element of type E obey the order,
specifying by following:

1\) pure key **keymaker function** E=&gt;K that extract some kind of
“key” from data elements of type E. In most cases with simple types E
the extract function is just *identical* function: f(x)=x.

If the E represent a kind of a record of database table or a case class
object the keymaker function may fetch a needed key field, e.g:
x=&gt;x.id . In other cases it may return a time stamp of an event
record object.

2\) **StreamOrder** object that specify the order of elements through
order of their keys. It contains:

2.a) the definition **order over keys K** using standard
***Ordering\[K\]*** object;

2.b) the **direction **of this order (ascending or descending) using
***OrderDirection*** constans ASC or DESC (DESC reverts the order);

2.c) the **unique flag** that asserts that keys should be unique, i.e.
no keys are repeating.

***The functions from ****StreamSort*****.**

The StreamSort object/class contains the collection of useful basic
method with such ***ordered ****streams***. As all of them are about
stream of E-elements with K-keys defined by ***keymaker function*** and
then ordered by StreamOrder over K – these parameters and arguments are
taken out to common class constructor (/apply method) and don’t emerge
in the method declarations.

**def **sort(distance: (E,E)=&gt;Boolean, bufSize: Int =
Int.*MaxValue*): Flow\[E, (E,Long), \_\]

- sort the **fuzzy ordered stream** of data with limited buffer.

“Fuzzy (pre)ordered” streams are those the sequences of elements that
can be sorted to ordered using limited buffer frame. The limit of buffer
here can be specified by:

1\) maximum amount of elements (**maximum buffer size**) – in other words
this means a fixed sized buffer is sufficient to derive the total order;
or

2\) the maximum **distance** from the current element. The distance is
specified by pure function (E,E)=&gt;Boolean that implied to return
**false** until two elements aren’t ***too far*** from each other by
some measure and then became **true** when they are ***too far*** and
the buffer shouldn’t store the new element.\
Note:

1\) If the you have the distance function given over the ***key
values***, i.e as (K,K)=&gt;Boolean it can be easily adduced to
(E,E)=&gt;Boolean combining with ***keymaker function*** (E)=&gt;K.

2\) If you need to limit buffer frame by time the source stream may be
zipped with timestamps.

3\) you may use either maximum buffer size (specify the distance function
as **(\_,\_)=&gt;false** ) or distance limit (specify maximum buffer
size as ***Int.MaxValue***) or both.

The output stream are zipped with element’s original number that can be
easily eliminated by ***.map(\_.\_1)*** .

The example of “fuzzy ordered data stream” may be a stream events with
timestamps that mixed from several machines with clocks not ideally
synchronized. Still lets assume we can require that clocks difference
are no more than 10 minutes. Here the key values are timestamp, the
distance function is that checks that two timestamps are more than 10
minutes from each other.

**def **checkException(): Flow\[E, (Option\[Exception\],E), \_\]

- check if stream are ordered. Zips the stream with Option of error
indicator that is None if there is the order of signal the error if the
order is violated.

**def **checkBool(): Flow\[E, (Boolean,E), \_\]

- the same as previous but zips with just boolean flags of error (true
if order, false if it is violated).

**def **assert(): Flow\[E, E, \_\]

- throws the exception from ***checkException()*** above if the order of
data is violated.

filter(): Flow\[E, E, \_\]

- just rid of all unordered elements, keeping only those that obey the
order.

Note that any filter operations with sorted data stream preserve its
order.

**SortedJoin** methods:

This functions are about of **joining** two ordered streams (in the
meaning as join means in SQL). The joining are doing by extracted key
values, that means that the streams may have different types of elemtns
E1 and E2, but the same type of the keys K, and the some order of them
defined as StreamOrder\[K\].

The two source stream may have M1 and M2 materialized values, that can
be combined as usual in Akka (i.e using Keep) into result M materialized
value.

As with StreamSort object/class the common parameters (E1,M1,E2,M2,K)
and arguments (source streams, keymaker functions and others) are taken
out to common constructor (/apply method) and don’t emerge in the method
declarations of SortedJoin object/class.

Note than all join operation over the key K preserve the output stream
order as order of keys K.

**def **fullJoin\[M\](combine: (M1,M2)=&gt;M):
Source\[(K,(Seq\[E1\],Seq\[E2\])),M\]

- group elements from both input streams using common keys.

The result comes with corresponding keys. If keys are not needed in the
output their may be easy omitted using ***.map(\_.\_****2****)*** .

**def **join\[M\](combine: (M1,M2)=&gt;M): Source\[(K,(E1,E2)),M\]

- do **simple join** thats means to get a couples of elements from both
input streams, if they have same key value.

The result comes paired with keys. If keys are not needed in the output
their may be easy omitted using ***.map(\_.\_****2****)*** .

**def **leftJoin\[M\](combine: (M1,M2)=&gt;M):
Source\[(K,(E1,Seq\[E2\])),M\]

- do **left join**: for every element of the *first* stream provide a
set of all corresponding by key value elements of the *second* stream.
If no elements of the second stream has this key then the empty
collection appears.

The result comes paired with keys. If keys are not needed in the output
their may be easy omitted using ***.map(\_.\_2)*** .

**def **rightJoin\[M\](combine: (M1,M2)=&gt;M):
Source\[(K,(Seq\[E1\],E2)),M\]

- do **right join**: for every element of the *second* stream provide a
set of all corresponding by key value elements of the *first* stream. If
no elements of the first stream has this key then the empty collection
appears.

The result comes paired with keys. If keys are not needed in the output
their may be easy omitted using ***.map(\_.\_2)*** .


