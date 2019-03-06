package distributed.core.beans;

import java.io.Serializable;
import java.security.PublicKey;

import distributed.core.entities.Blockchain;
import distributed.core.entities.Transaction;

/*
 * Todo : Message Class Contains anything that will be sent above the network
 * Αντί να έχουμε type μηνύματος να έχουμε υποκλάσεις (abstract class ή interfaces ή subclass) για κάθε διαφορετικό είδος μηνύματος;
 * Βέβαια για κάθε υποκλάση θα πρέπει με κάποιο τρόπο να μπορεί να αναγνωριστεί από το server
 * οπότε πάλι να έχει πεδίο type(να δούμε αν μπορούμε να πάρουμε το instance) αλλά τίποτα άλλο περιττό
 *
 * Για την περίπτωση μας βολεύει κάθε διαφορετικό είδος μηνύματος να είναι μια υποκλάση όπου να υλοποιείται οτιδήποτε συγκεκριμένο
 * Η υπερκλάση θα είναι abstract καθώς ποτέ δε θα φτιάξουμε αντικείμενα αυτής, και απλά εκεί θα συγκεντρώνεται οτιδήποτε κοινό.
 * Αν δεν υπάρχει τπτ κοινό θα μπορούσε να την παραλείψουμε και να έχουμε μόνο object.
 */
public abstract class Message implements Serializable {

	private static final long serialVersionUID = 1L;

	// private MessageType type;
	// private Blockchain blockchain;
	// private Transaction transaction;

}
