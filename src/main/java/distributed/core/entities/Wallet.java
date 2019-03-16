package distributed.core.entities;

import java.io.Serializable;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Wallet implements Serializable { // TODO make it singleton

	private static final Logger LOG = LoggerFactory.getLogger(Wallet.class.getName());

	private PrivateKey privateKey;
	private PublicKey publicKey;

	public HashMap<String, TransactionOutput> utxids = new HashMap<String, TransactionOutput>(); // only UTXOs owned by this wallet.

	public Wallet() {
		generateKeyPair();
	}

	public HashMap<String, TransactionOutput> getUtxids() {
		return utxids;
	}

	public PrivateKey getPrivateKey() {
		return privateKey;
	}

	public PublicKey getPublicKey() {
		return publicKey;
	}

	/**
	 * Function generating a new Keypair of public and private key for this
	 * wallet
	 *
	 * @return
	 */
	public void generateKeyPair() {
		try {
			// Create a generator for Public and Private Key Pair
			// ECDSA : variant of the Digital Signature Algorithm (DSA) which uses elliptic
			// curve cryptography
			// BC : provider for algorithms, you could also use Java's builtin
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance("ECDSA", "BC");
			// Secure random number generator that provides random numbers according to the
			// algorithm that is given as input
			SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
			// Parameters defined to use for the elliptic curve cryptography
			ECGenParameterSpec ecSpec = new ECGenParameterSpec("prime192v1");
			// Initialize the key generator and generate a KeyPair
			keyGen.initialize(ecSpec, random); // 256 bytes provides an acceptable security level
			KeyPair keyPair = keyGen.generateKeyPair();
			privateKey = keyPair.getPrivate();
			publicKey = keyPair.getPublic();
		} catch (Exception e) {
			e.printStackTrace();

		}
	}

	/**
	 * Get the balance on this wallet
	 *
	 * @param allUTXOs
	 *            (unspent transactions)
	 * @return the balance as float
	 */
	public float getBalance(ConcurrentHashMap<String, TransactionOutput> allUTXOs) {
		LOG.info("Start getBalance");

		float total = 0;
		for (HashMap.Entry<String, TransactionOutput> entry : allUTXOs.entrySet()) {
			TransactionOutput utxid = entry.getValue();
			if (utxid.isMine(publicKey)) {
				utxids.put(utxid.getId(), utxid); // σε αυτή τη δομή κρατάμε τα trans που ανήκουν σε εμάς.
				total += utxid.getValue();
			}
		}
		return total;
	}

	/**
	 * Creates and returns a transaction from this wallet to a recipient knowing
	 * its
	 * public key
	 *
	 * @param _recipient
	 * @param value
	 * @param allUTXOs
	 * @return
	 */
	public Transaction sendFunds(PublicKey _recipient, float value,
			ConcurrentHashMap<String, TransactionOutput> allUTXOs) {
		LOG.info("Starting sendFunds");

		if (getBalance(allUTXOs) < value) {
			LOG.info("Not enough coins");
			return null;
		}

		// σαν inputs δίνουμε unspent transactions outputs που έχουν γίνει προς εμάς
		// δικαιολογώντας έτσι την προέλευση των χρημάτων
		ArrayList<TransactionInput> inputs = new ArrayList<TransactionInput>();

		float total = 0;
		for (Map.Entry<String, TransactionOutput> item : utxids.entrySet()) {
			TransactionOutput utxo = item.getValue();
			total += utxo.getValue();
			inputs.add(new TransactionInput(utxo.getId()));
			if (total > value) {
				LOG.debug("Input transactions covers the amount to be sent");
				break; // μόλις συμπληρωθεί το ποσό σταμάτα.
			}
		}

		Transaction newTransaction = new Transaction(publicKey, _recipient, value, inputs);
		newTransaction.generateSignature(privateKey); // sign the trasaction

		// αφαιρούμε τα output transaction που χρησιμοποιήθηκαν για να δικαιολογήσουν
		// το νέο transaction από τη λίστα από τα unspend καθώς πλέον είναι spent.
		// ΕΔΩ αφαιρούνται από την λίστα των unspent του συγκεκριμένου πορτοφολιού
		// ΠΩς αφαιρούνται γενικά ;
		for (TransactionInput input : inputs) {
			utxids.remove(input.transactionOutputId);
		}

		return newTransaction;
	}
}
