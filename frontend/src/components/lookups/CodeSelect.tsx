import { Select } from 'antd';
import { useLookupCodes } from '../../features/lookups/useLookups';

interface CodeSelectProps {
  /** Lookup set name (e.g. "countries", "currencies"). */
  set: string;
  value?: string;
  onChange?: (value: string | undefined) => void;
  placeholder?: string;
  allowClear?: boolean;
  disabled?: boolean;
}

/**
 * Searchable dropdown backed by a backend lookup set, so the choices always match server validation.
 * Designed as a Form.Item child (AntD injects value/onChange). The backend returns bare codes (no
 * labels yet — placeholder seeds), so code == label here.
 */
export function CodeSelect({
  set,
  value,
  onChange,
  placeholder,
  allowClear = true,
  disabled,
}: CodeSelectProps) {
  const { data, isLoading } = useLookupCodes(set);
  const options = (data ?? []).map((code) => ({ value: code, label: code }));
  return (
    <Select
      showSearch
      value={value}
      onChange={onChange}
      placeholder={placeholder}
      allowClear={allowClear}
      disabled={disabled}
      loading={isLoading}
      options={options}
      optionFilterProp="label"
      style={{ width: '100%' }}
    />
  );
}
