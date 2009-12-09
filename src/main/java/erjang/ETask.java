/**
 * This file is part of Erjang - A JVM-based Erlang VM
 *
 * Copyright (c) 2009 by Trifork
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package erjang;

import java.util.Set;
import java.util.TreeSet;

import kilim.Mailbox;
import kilim.Pausable;

/**
 * An ETask is what is common for processes and open ports
 */
public abstract class ETask<H extends EHandle> extends kilim.Task {

	protected static final EObject am_normal = EAtom.intern("normal");
	protected static final EObject am_java_exception = EAtom
			.intern("java_exception");

	/**
	 * @return
	 */
	public abstract H self();

	private Set<EHandle> links = new TreeSet<EHandle>();

	public void unlink(EHandle handle) {
		links.remove(handle);
	}
	
	/**
	 * @param task
	 * @throws Pausable 
	 */
	public void link_to(ETask<?> task) throws Pausable {
		link_oneway(task.self());
		task.self().link_oneway((EHandle) self());
	}

	public void link_to(EHandle handle) throws Pausable {
		link_oneway(handle);
		handle.link_oneway((EHandle) self());
	}

	public void link_oneway(EHandle h) throws Pausable {
		// TODO: check if h is valid.
		
		if (h.exists()) {
			links.add(h);
		} else {
			link_failure(h);
		}
	}

	/**
	 * @param h
	 * @throws Pausable 
	 */
	protected void link_failure(EHandle h) throws Pausable {
		throw new ErlangError(ERT.am_noproc);
	}

	protected void send_exit_to_all_linked(EObject result) throws Pausable {
		H me = self();
		for (EHandle handle : links) {
			handle.exit_signal(me, result);
		}
	}

	protected Mailbox<EObject> mbox = new Mailbox<EObject>();

	protected static enum State {
		INIT, // has not started yet
		RUNNING, // is live
		EXIT_SIG, // received exit signal
		DONE
		// done
	};

	protected State pstate = State.INIT;
	protected EObject exit_reason;

	/**
	 * @return
	 */
	public EObject mbox_peek() {
		check_exit();
		return mbox.peek();
	}

	/**
	 * @throws Pausable
	 * 
	 */
	public void mbox_wait() throws Pausable {
		mbox.untilHasMessage();
	}

	/**
	 * @param longValue
	 */
	public boolean mbox_wait(long timeoutMillis) throws Pausable {
		return mbox.untilHasMessage(timeoutMillis);
	}

	/**
	 * @param msg
	 * @throws Pausable
	 */
	public void mbox_send(EObject msg) throws Pausable {
		mbox.put(msg);
	}

	/**
	 * @return
	 * @throws Pausable
	 */
	public void mbox_remove_one() throws Pausable {
		mbox.get();
	}

	/**
	 * @param from
	 * @param reason
	 */
	public final void send_exit(EHandle from, EObject reason) throws Pausable {

		System.err.println("exit " + from.task() + " -> " + this);

		// ignore exit signals from myself
		if (from == self()) {
			return;
		}

		synchronized (this) {
			switch (pstate) {

			// process is already "done", just ignore exit signal
			case DONE:
				return;

				// we have already received one exit signal, ignore
				// subsequent ones...
			case EXIT_SIG:
				// TODO: warn that this process is not yet dead. why?
				return;

				// the process is not running yet, this should not happen
			case INIT:
				throw new Error(
						"cannot receive exit signal before we're running");

			default:
				throw new Error("unknown state?");

			case RUNNING:
			}
		}

		process_incoming_exit(from, reason);

	}

	protected abstract void process_incoming_exit(EHandle from, EObject reason)
			throws Pausable;

	/**
	 * will check if this process have received an exit signal (and we're not
	 * trapping)
	 */
	public final void check_exit() {
		if (this.pstate == State.EXIT_SIG) {
			throw new ErlangExitSignal(exit_reason);
		}
	}

	/**
	 * @return
	 */
	public Mailbox<EObject> mbox() {
		return mbox;
	}

	/**
	 * @return
	 */
	public boolean exists() {
		return pstate == State.INIT || pstate == State.RUNNING;
	}

}
