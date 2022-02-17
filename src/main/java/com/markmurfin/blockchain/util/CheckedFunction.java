package com.markmurfin.blockchain.util;

import lombok.NonNull;

import java.util.concurrent.Callable;

@FunctionalInterface
public interface CheckedFunction<T, R, E extends Exception>
{
	R apply(T t) throws E;

	static <T, R> Callable<R> closure(@NonNull final T t, @NonNull final CheckedFunction<T, R, ? extends Exception> checkedFunction)
	{
		return () -> checkedFunction.apply(t);
	}
}
